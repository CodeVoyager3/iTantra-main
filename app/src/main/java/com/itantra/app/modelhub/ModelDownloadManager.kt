package com.itantra.app.modelhub

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/** Per-language lifecycle state of a model pack download. */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState

    data class Downloading(val progressBytes: Long, val totalBytes: Long) : ModelDownloadState {
        /** Fraction complete in 0..1. */
        val progress: Float
            get() = if (totalBytes > 0) {
                (progressBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
    }

    data object Verifying : ModelDownloadState
    data object Extracting : ModelDownloadState
    data object Installed : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}

/**
 * A catalogue entry joined with its live download state and on-disk
 * installation flag. This is the UI-facing shape of the model hub.
 */
data class LanguageModelPack(
    val languageTag: String,
    val name: String,
    val script: String,
    val iso: String,
    val sizeMb: Double,
    val sha256: String,
    val isInstalled: Boolean,
    val downloadState: ModelDownloadState
)

/**
 * Downloads, verifies and extracts language packs on demand using plain
 * [HttpURLConnection] — no networking libraries. A pack is downloaded to a
 * temp file in [Context.getCacheDir], SHA-256 verified against the catalogue,
 * then extracted into `filesDir/models/{languageTag}/`. The temp file is
 * deleted on completion or failure, duplicate concurrent downloads of the
 * same language are refused, and already-installed packs are not re-fetched.
 *
 * All blocking work runs on [Dispatchers.IO]; state updates are posted back
 * to the main dispatcher.
 */
class ModelDownloadManager(
    private val context: Context,
    private val storageManager: ModelStorageManager,
    private val scope: CoroutineScope,
    private val onPackInstalled: suspend (String) -> Unit = {}
) {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val PROGRESS_UPDATE_INTERVAL_MS = 200L
        const val IO_BUFFER_BYTES = 64 * 1024
        const val USER_AGENT = "iTantra/2.4.0 (offline-first disaster mesh)"
    }

    private val _states = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())

    /** Download state per language tag. */
    val states: StateFlow<Map<String, ModelDownloadState>> = _states.asStateFlow()

    private val activeTags = ConcurrentHashMap.newKeySet<String>()

    /**
     * Starts downloading [language] if it is not already installed and no
     * download for it is already in flight.
     */
    fun download(language: CatalogueLanguage) {
        val tag = language.languageTag
        if (!activeTags.add(tag)) return // duplicate concurrent download refused

        if (storageManager.isInstalled(tag)) {
            // Verified pack is already on disk — nothing to do.
            activeTags.remove(tag)
            _states.update { it + (tag to ModelDownloadState.Installed) }
            return
        }

        _states.update { it + (tag to ModelDownloadState.Downloading(0L, language.sizeBytes)) }

        scope.launch {
            try {
                val finalState = withContext(Dispatchers.IO) { downloadAndInstall(language) }
                _states.update { it + (tag to finalState) }
                if (finalState is ModelDownloadState.Installed) {
                    storageManager.rescan()
                    onPackInstalled(tag)
                }
            } finally {
                activeTags.remove(tag)
            }
        }
    }

    /** Clears the state entry for [languageTag] (e.g. after a pack deletion). */
    fun resetState(languageTag: String) {
        _states.update { it - languageTag }
    }

    /** Clears all state entries (e.g. after an emergency wipe). */
    fun clearStates() {
        _states.value = emptyMap()
    }

    private suspend fun downloadAndInstall(language: CatalogueLanguage): ModelDownloadState {
        val tag = language.languageTag
        val tempFile = File(context.cacheDir, "${language.archive}.part")
        val destDir = File(File(context.filesDir, "models"), tag)

        try {
            // A leftover partial extraction would block the fresh install.
            if (destDir.exists()) destDir.deleteRecursively()

            // --- Phase 1: stream the archive to a temp file ----------------
            val connection = URL(ModelCatalogue.DOWNLOAD_BASE_URL + language.archive)
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (connection.responseCode !in 200..299) {
                    tempFile.delete()
                    return ModelDownloadState.Error("Server responded HTTP ${connection.responseCode}")
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                    ?: language.sizeBytes
                var readBytes = 0L
                var lastUpdateAt = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(IO_BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            readBytes += count
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                                lastUpdateAt = now
                                updateStateOnMain(tag) {
                                    ModelDownloadState.Downloading(readBytes, totalBytes)
                                }
                            }
                        }
                    }
                }
                updateStateOnMain(tag) { ModelDownloadState.Downloading(readBytes, totalBytes) }
            } finally {
                connection.disconnect()
            }

            // --- Phase 2: verify the archive against the catalogue SHA-256 --
            updateStateOnMain(tag) { ModelDownloadState.Verifying }
            val actualSha256 = sha256Of(tempFile)
            if (!actualSha256.equals(language.sha256, ignoreCase = true)) {
                tempFile.delete()
                return ModelDownloadState.Error("SHA-256 verification failed")
            }

            // --- Phase 3: extract into filesDir/models/{languageTag} --------
            updateStateOnMain(tag) { ModelDownloadState.Extracting }
            extractZip(tempFile, destDir)
            tempFile.delete()
            return ModelDownloadState.Installed
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            return ModelDownloadState.Error(e.message ?: "Download failed")
        }
    }

    /** Hops to the main dispatcher to publish a state transition. */
    private suspend fun updateStateOnMain(tag: String, state: () -> ModelDownloadState) {
        withContext(Dispatchers.Main.immediate) {
            _states.update { it + (tag to state()) }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(IO_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extracts [zipFile] into [destDir], rejecting entries that would escape
     * the destination directory (zip-slip protection).
     */
    private fun extractZip(zipFile: File, destDir: File) {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath

        ZipInputStream(FileInputStream(zipFile).buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name).canonicalFile
                if (!target.path.startsWith(canonicalDest + File.separator)) {
                    throw IOException("Archive entry escapes destination: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
