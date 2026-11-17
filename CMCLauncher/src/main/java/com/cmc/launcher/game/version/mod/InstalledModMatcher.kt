package com.cmc.launcher.game.version.mod

import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.getCFFilesByFingerprints
import com.cmc.launcher.game.download.addon.platform.getModrinthVersBySha1
import com.cmc.launcher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "InstalledModMatcher"

private const val MATCH_BATCH_SIZE = 25

private const val MATCH_PARALLELISM = 4

class MatchedInstalledMods(
    val byProject: Map<String, InstalledMod>,
    val byVersion: Map<String, InstalledMod>,
    val complete: Boolean
)

suspend fun scanModFingerprints(modsDir: File): List<ModFingerprints> =
    withContext(Dispatchers.IO) {
        val files = modsDir.listFiles()
            ?.filter { it.isFile && it.isModFileCandidate() }
            ?: return@withContext emptyList()

        val semaphore = Semaphore(READER_PARALLELISM)
        coroutineScope {
            files.map { file ->
                async {
                    semaphore.withPermit {
                        try {
                            computeModFingerprints(file)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Logger.warning(TAG, "Failed to compute mod fingerprints: ${file.name}", e)
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

suspend fun matchInstalledMods(
    fingerprints: List<ModFingerprints>,
    platform: Platform,
    onCollect: suspend (MatchedInstalledMods) -> Unit = {}
): MatchedInstalledMods {
    val mutex = Mutex()
    val byProject = mutableMapOf<String, InstalledMod>()
    val byVersion = mutableMapOf<String, InstalledMod>()
    if (fingerprints.isEmpty()) {
        return MatchedInstalledMods(byProject, byVersion, complete = true)
    }

    val pending = mutableListOf<InstalledMod>()
    var complete = true

    fun collect(installed: InstalledMod) {
        if (installed.notFound) return
        byProject[installed.projectId] = installed
        byVersion[installed.versionId] = installed
        pending.add(installed)
    }

    suspend fun flush() {
        if (pending.isEmpty()) return
        onCollect(
            MatchedInstalledMods(
                byProject = pending.associateBy { it.projectId },
                byVersion = pending.associateBy { it.versionId },
                complete = false
            )
        )
        pending.clear()
    }

    val cache = installedModCache()
    val uncached = mutableListOf<ModFingerprints>()
    mutex.withLock {
        for (print in fingerprints) {
            val cached = cache.decodeParcelable(print.cacheKey(platform), InstalledMod::class.java)
            if (cached != null) collect(cached) else uncached.add(print)
        }
        flush()
    }

    coroutineScope {
        val semaphore = Semaphore(MATCH_PARALLELISM)
        uncached.chunked(MATCH_BATCH_SIZE).map { chunk ->
            async {
                semaphore.withPermit {
                    val fetched = try {
                        when (platform) {
                            Platform.MODRINTH -> getModrinthVersBySha1(chunk.map { it.sha1 })
                            Platform.CURSEFORGE ->
                                getCFFilesByFingerprints(chunk.map { it.murmur2 }).mapKeys { it.key.toString() }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Logger.warning(TAG, "Failed to match installed mods on platform: $platform", e)
                        null
                    }

                    mutex.withLock {
                        if (fetched == null) {
                            complete = false
                        } else {
                            for (print in chunk) {
                                val installed = fetched[print.fingerprintValue(platform)]?.toInstalledMod()
                                    ?: notFoundMod(platform)
                                cache.encode(print.cacheKey(platform), installed, MMKV.ExpireInDay)
                                collect(installed)
                            }
                            flush()
                        }
                    }
                }
            }
        }.awaitAll()
    }

    val isComplete = mutex.withLock { complete }
    return MatchedInstalledMods(byProject, byVersion, isComplete)
}

private val MOD_FILE_EXTENSIONS = setOf("jar", "zip", "litemod")

private fun File.isModFileCandidate(): Boolean {
    val extension = if (isDisabled()) {
        File(nameWithoutExtension).extension
    } else {
        extension
    }
    return extension.lowercase() in MOD_FILE_EXTENSIONS
}

private fun ModFingerprints.cacheKey(platform: Platform): String =
    "${platform.name}/${fingerprintValue(platform)}"

private fun ModFingerprints.fingerprintValue(platform: Platform): String = when (platform) {
    Platform.MODRINTH -> sha1
    Platform.CURSEFORGE -> murmur2.toString()
}

private fun notFoundMod(platform: Platform): InstalledMod = InstalledMod(
    platform = platform,
    projectId = "",
    versionId = "",
    versionName = "",
    notFound = true
)
