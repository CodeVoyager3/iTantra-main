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

    fun isInstalled(languageTag: String): Boolean =
        _installedPacks.value.containsKey(languageTag)

    /**
     * Deletes the extracted pack directory for [languageTag].
     *
     * @return the number of bytes freed (0 if it was not installed).
     */
    suspend fun deleteModel(languageTag: String): Long = withContext(Dispatchers.IO) {
        val dir = File(modelsDir, languageTag)
        val freedBytes = dirSizeBytes(dir)
        if (dir.exists()) dir.deleteRecursively()
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

    /** A pack counts as installed only if its extracted manifest is present. */
    private fun scanInstalled(): Map<String, Long> {
        val children = modelsDir.listFiles() ?: return emptyMap()
        return children
            .filter { it.isDirectory && File(it, "manifest.json").exists() }
            .associate { it.name to dirSizeBytes(it) }
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp().sumOf { if (it.isFile) it.length() else 0L }
    }
}
