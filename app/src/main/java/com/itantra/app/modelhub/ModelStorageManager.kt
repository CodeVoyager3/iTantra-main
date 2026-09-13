package com.itantra.app.modelhub

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the on-disk layout of the offline model hub:
 *
 *  - `filesDir/models/{languageTag}/`  — extracted language packs
 *  - `cacheDir/map_tiles/`             — cached offline map tiles
 *
 * Exposes installed packs (tag -> on-disk bytes) as a [StateFlow] and all
 * destructive operations return the number of bytes freed.
 */
class ModelStorageManager(context: Context) {

    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "models")
    private val mapCacheDir = File(appContext.cacheDir, "map_tiles")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _installedPacks = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Installed pack language tags mapped to their on-disk size in bytes. */
    val installedPacks: StateFlow<Map<String, Long>> = _installedPacks.asStateFlow()

    init {
        refresh()
    }

    /** Asynchronously re-scans the models directory. */
    fun refresh() {
        scope.launch { rescan() }
    }

    /** Synchronously re-scans the models directory and updates the flow. */
    suspend fun rescan(): Map<String, Long> = withContext(Dispatchers.IO) {
        scanInstalled().also { _installedPacks.value = it }
    }

    fun installedTags(): Set<String> = _installedPacks.value.keys

    fun isInstalled(languageTag: String): Boolean {
        val keys = _installedPacks.value.keys
        if (keys.contains(languageTag) || keys.any {
            it.startsWith("$languageTag-", ignoreCase = true) || it.equals(languageTag, ignoreCase = true)
        }) return true
        return isInstalledOnDisk(languageTag)
    }

    /**
     * Cheap synchronous on-disk check with the same semantics as [isInstalled],
     * but independent of the async [refresh] rescan (the flow starts empty, so
     * [isInstalled] returns false for real packs until the first scan lands).
     * Does a single `listFiles` on the models directory — safe to call from
     * Dispatchers.Default/IO, e.g. the mesh voice receiver deciding between
     * ONNX TTS and system TTS for an incoming packet.
     */
    fun isInstalledOnDisk(languageTag: String): Boolean {
        val children = modelsDir.listFiles() ?: return false
        return children.any { dir ->
            dir.isDirectory && LanguageTags.matches(dir.name, languageTag) && looksLikePack(dir)
        }
    }

    /**
     * Deletes the extracted pack directory for [languageTag].
     *
     * @return the number of bytes freed (0 if it was not installed).
     */
    suspend fun deleteModel(languageTag: String): Long = withContext(Dispatchers.IO) {
        val targetDir = File(modelsDir, languageTag).takeIf { it.exists() }
            ?: modelsDir.listFiles()?.firstOrNull {
                it.isDirectory && (it.name.startsWith("$languageTag-", ignoreCase = true) || it.name.equals(languageTag, ignoreCase = true))
            }
        val freedBytes = if (targetDir != null) dirSizeBytes(targetDir) else 0L
        if (targetDir != null && targetDir.exists()) targetDir.deleteRecursively()
        _installedPacks.value = scanInstalled()
        freedBytes
    }

    /**
     * Deletes the offline map tile cache.
     *
     * @return the number of bytes freed.
     */
    suspend fun clearMapCache(): Long = withContext(Dispatchers.IO) {
        val freedBytes = dirSizeBytes(mapCacheDir)
        if (mapCacheDir.exists()) mapCacheDir.deleteRecursively()
        freedBytes
    }

    /** Current size of the offline map tile cache, in bytes. */
    fun mapCacheSizeBytes(): Long = dirSizeBytes(mapCacheDir)

    /**
     * Emergency wipe: removes the entire models directory and the map tile
     * cache. Settings clearing is coordinated by the caller via
     * [com.itantra.app.data.SettingsRepository.clearAll].
     *
     * @return the total number of bytes freed.
     */
    suspend fun wipeAll(): Long = withContext(Dispatchers.IO) {
        val freedBytes = dirSizeBytes(modelsDir) + dirSizeBytes(mapCacheDir)
        if (modelsDir.exists()) modelsDir.deleteRecursively()
        if (mapCacheDir.exists()) mapCacheDir.deleteRecursively()
        _installedPacks.value = emptyMap()
        freedBytes
    }

    /**
     * Checks if the neural translation engine is installed.
     */
    fun isTranslationInstalled(): Boolean {
        val targetDir = File(modelsDir, "nmt-hi-en")
        return targetDir.exists() && (looksLikePack(targetDir) || File(targetDir, "manifest.json").exists())
    }

    /**
     * Creates or installs the translation engine directory structure on disk.
     */
    suspend fun installTranslationModelSimulated(): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(modelsDir, "nmt-hi-en")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val manifest = File(targetDir, "manifest.json")
        if (!manifest.exists()) {
            manifest.writeText(
                """{"id":"nmt-hi-en","name":"Hindi-English Neural NMT","type":"translation","version":"1.0.0","sizeMb":48.5}"""
            )
        }
        rescan()
        true
    }

    /**
     * Deletes the neural translation engine from disk.
     */
    suspend fun deleteTranslationModel(): Long = withContext(Dispatchers.IO) {
        val targetDir = File(modelsDir, "nmt-hi-en")
        val freedBytes = if (targetDir.exists()) dirSizeBytes(targetDir) else 0L
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        rescan()
        freedBytes
    }

    fun getTranslationModelDir(): File = File(modelsDir, "nmt-hi-en")

    /** A pack counts as installed if its manifest, stt/tts subfolders, or onnx model files exist. */
    private fun looksLikePack(dir: File): Boolean =
        File(dir, "manifest.json").exists() ||
            File(dir, "stt").exists() ||
            File(dir, "tts").exists() ||
            dir.name.equals("nmt-hi-en", ignoreCase = true) ||
            (dir.listFiles()?.any { f -> f.extension.equals("onnx", ignoreCase = true) || f.isDirectory } == true)

    private fun scanInstalled(): Map<String, Long> {
        val children = modelsDir.listFiles() ?: return emptyMap()
        return children
            .filter { it.isDirectory && looksLikePack(it) }
            .associate { it.name to dirSizeBytes(it) }
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp().sumOf { if (it.isFile) it.length() else 0L }
    }
}
