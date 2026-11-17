package com.cmc.launcher.game.download.addon.platform

import android.util.Log
import com.cmc.launcher.game.addons.mirror.MirrorPriority
import com.cmc.launcher.game.addons.mirror.resolveMirrorPriority
import com.cmc.launcher.game.download.addon.mapExceptionToMessage
import com.cmc.launcher.game.download.addon.platform.curseforge.CurseForgeSearcher
import com.cmc.launcher.game.download.addon.platform.curseforge.MCIM_CURSEFORGE_API
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeFile
import com.cmc.launcher.game.download.addon.platform.modrinth.MCIM_MODRINTH_API
import com.cmc.launcher.game.download.addon.platform.modrinth.ModrinthSearcher
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthVersion
import com.cmc.launcher.game.download.addon.utils.localizedModSearchKeywords
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.screens.content.download.addon.elements.DownloadAddonState
import com.cmc.launcher.ui.screens.content.download.addon.elements.SearchAddonState
import com.cmc.launcher.utils.isChinaMainland
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.isInterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "PlatformSearch"

private val modrinthSearcher = ModrinthSearcher()
private val mirrorModrinthSearcher = ModrinthSearcher(
    api = MCIM_MODRINTH_API,
    source = "MCIM Modrinth"
)

private val curseForgeSearcher = CurseForgeSearcher()
private val mirrorCurseForgeSearcher = CurseForgeSearcher(
    api = MCIM_CURSEFORGE_API,
    source = "MCIM CurseForge"
)

suspend fun <E: AbstractPlatformSearcher, T> mirroredPlatformSearcher(
    searchers: List<E>,
    printLog: Boolean = true,
    block: suspend (E) -> T
): T {
    require(searchers.isNotEmpty()) { "Searcher list must not be empty." }

    val errors = mutableListOf<Exception>()
    var lastException: Exception? = null

    for (searcher in searchers) {
        try {
            if (printLog) {
                Logger.debug(TAG, "Starting to attempt to perform the operation on source: {${searcher.source}}")
            }
            return block(searcher)
        } catch (e: Exception) {
            Log.w("PlatformSearcher", "Failed to perform the operation on source: {${searcher.source}}", e)
            lastException = e

            if (e.isInterruptedIOException()) {
                throw e
            } else if (e is FileNotFoundException) {
                errors.add(e)
                break
            } else {
                errors.add(e)
            }
        }
    }

    if (printLog) {
        Logger.warning(TAG,
            msg = "An error occurred during this search.",
            t = IOException("All sources have failed to attempt", lastException).apply {
                errors.forEachIndexed { i, e ->
                    addSuppressed(Exception("Mirror error #${i + 1}: ${e.message}"))
                }
            }
        )
    }
    throw lastException ?: IllegalStateException("Should not have executed to this stage.")
}

fun mirroredCurseForgeSource(
    enabledMirror: Boolean = isChinaMainland()
): List<CurseForgeSearcher> {
    val source = resolveMirrorPriority(AllSettings.addonPlatformSource.getValue(), mainland = enabledMirror)
    val mirrorSource = mirrorCurseForgeSearcher.takeIf { enabledMirror }
    return when (source) {
        MirrorPriority.OFFICIAL -> listOf(curseForgeSearcher)
        MirrorPriority.MIRROR_FIRST ->
            listOfNotNull(mirrorSource, curseForgeSearcher)
    }
}

fun mirroredModrinthSource(
    enabledMirror: Boolean = isChinaMainland()
): List<ModrinthSearcher> {
    val source = resolveMirrorPriority(AllSettings.addonPlatformSource.getValue(), mainland = enabledMirror)
    val mirrorSource = mirrorModrinthSearcher.takeIf { enabledMirror }
    return when (source) {
        MirrorPriority.OFFICIAL -> listOf(modrinthSearcher)
        MirrorPriority.MIRROR_FIRST ->
            listOfNotNull(mirrorSource, modrinthSearcher)
    }
}

suspend fun searchAddons(
    searchPlatform: Platform,
    searchFilter: PlatformSearchFilter,
    platformClasses: PlatformClasses,
    onSuccess: suspend (PlatformSearchResult) -> Unit,
    onError: (SearchAddonState.Error) -> Unit
) {
    runCatching {
        val (containsChinese, englishKeywords) = searchFilter.searchName.localizedModSearchKeywords(platformClasses)

        val queries = englishKeywords?.takeIf { it.isNotEmpty() }?.toList()
            ?: listOf(searchFilter.searchName)

        val searchFilterForQuery = englishKeywords?.takeIf { it.isNotEmpty() }
            ?.let { searchFilter.copy(sortField = PlatformSortField.RELEVANCE) }
            ?: searchFilter

        var lastResult: PlatformSearchResult? = null
        var lastException: Exception? = null
        for (query in queries) {
            try {
                val r = when (searchPlatform) {
                    Platform.CURSEFORGE -> mirroredPlatformSearcher(
                        searchers = mirroredCurseForgeSource(),
                        printLog = false
                    ) { searcher ->
                        searcher.searchAddons(
                            query = query,
                            searchFilter = searchFilterForQuery,
                            platformClasses = platformClasses
                        )
                    }
                    Platform.MODRINTH -> mirroredPlatformSearcher(
                        searchers = mirroredModrinthSource(),
                        printLog = false
                    ) { searcher ->
                        searcher.searchAddons(
                            query = query,
                            searchFilter = searchFilterForQuery,
                            platformClasses = platformClasses
                        )
                    }
                }
                lastResult = r
                if (r.getAddonPage(platformClasses).data.isNotEmpty()) break
            } catch (e: Exception) {

                lastException = e
            }
        }

        val result = lastResult ?: throw lastException ?: IOException("Failed to search for all queries")

        onSuccess(
            if (containsChinese) result.processChineseSearchResults(searchFilter.searchName, platformClasses)
            else result
        )
    }.onFailure { e ->
        if (e !is CancellationException) {
            Logger.error(TAG, "An exception occurred while searching for addons.", e)
            val state = SearchAddonState.Error(mapExceptionToMessage(e))
            onError(state)
        } else {
            Logger.debug(TAG, "The search task has been cancelled.")
        }
    }
}

suspend fun getVersions(
    projectID: String,
    platform: Platform,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _, _ -> },
) = when (platform) {
    Platform.CURSEFORGE -> mirroredPlatformSearcher(
        searchers = mirroredCurseForgeSource()
    ) { searcher ->
        searcher.getVersions(
            projectID = projectID,
            pageCallback = pageCallback
        )
    }
    Platform.MODRINTH -> mirroredPlatformSearcher(
        searchers = mirroredModrinthSource()
    ) { searcher ->
        searcher.getVersions(
            projectID = projectID,
            pageCallback = pageCallback
        )
    }
}

suspend fun <E> getVersions(
    projectID: String,
    platform: Platform,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _, _ -> },
    onSuccess: suspend (List<PlatformVersion>) -> Unit,
    onError: (DownloadAddonState<List<E>>) -> Unit
) {
    runCatching {
        val result = getVersions(projectID, platform, pageCallback)
        onSuccess(result)
    }.onFailure { e ->
        if (e !is CancellationException) {
            Logger.error(TAG, "An exception occurred while retrieving the project version.", e)
            val state = DownloadAddonState.Error<List<E>>(mapExceptionToMessage(e))
            onError(state)
        } else {
            Logger.debug(TAG, "The version retrieval task has been cancelled.")
        }
    }
}

suspend fun <E> getProject(
    projectID: String,
    platform: Platform,
    onSuccess: (PlatformProject) -> Unit,
    onError: (DownloadAddonState<E>, Throwable) -> Unit
) {
    runCatching {
        when (platform) {
            Platform.CURSEFORGE -> mirroredPlatformSearcher(
                searchers = mirroredCurseForgeSource()
            ) { searcher ->
                searcher.getProject(projectID)
            }
            Platform.MODRINTH -> mirroredPlatformSearcher(
                searchers = mirroredModrinthSource()
            ) { searcher ->
                searcher.getProject(projectID)
            }
        }
    }.fold(
        onSuccess = onSuccess,
        onFailure = { e ->
            if (e !is CancellationException) {
                Logger.error(TAG, "An exception occurred while retrieving project information.", e)
                val state = DownloadAddonState.Error<E>(mapExceptionToMessage(e))
                onError(state, e)
            } else {
                Logger.debug(TAG, "The project retrieval task has been cancelled.")
            }
        }
    )
}

suspend fun getProjectByVersion(
    projectId: String,
    platform: Platform,
    printLog: Boolean = true
): PlatformProject = withContext(Dispatchers.IO) {
    when (platform) {
        Platform.MODRINTH -> mirroredPlatformSearcher(
            searchers = mirroredModrinthSource(),
            printLog = printLog
        ) { searcher ->
            searcher.getProject(projectId)
        }
        Platform.CURSEFORGE -> mirroredPlatformSearcher(
            searchers = mirroredCurseForgeSource(),
            printLog = printLog
        ) { searcher ->
            searcher.getProject(projectId)
        }
    }
}

suspend fun getVersionById(
    versionId: String,
    platform: Platform,
    printLog: Boolean = true
): PlatformVersion = withContext(Dispatchers.IO) {
    when (platform) {
        Platform.MODRINTH -> mirroredPlatformSearcher(
            searchers = mirroredModrinthSource(),
            printLog = printLog
        ) { searcher ->
            searcher.getVersion(versionId)
        }
        Platform.CURSEFORGE -> error("CurseForge dependencies do not carry a version id.")
    }
}

suspend fun getVersionByLocalFile(file: File, sha1: String): PlatformVersion? = withContext(Dispatchers.IO) {
    coroutineScope {
        val modrinthDeferred = async(Dispatchers.IO) {
            runCatching {
                mirroredPlatformSearcher(
                    searchers = mirroredModrinthSource(),
                    printLog = false
                ) { searcher ->
                    searcher.getVersionByLocalFile(file, sha1)
                }
            }.getOrNull()
        }

        val curseForgeDeferred = async(Dispatchers.IO) {
            runCatching {
                mirroredPlatformSearcher(
                    searchers = mirroredCurseForgeSource(),
                    printLog = false
                ) { searcher ->
                    searcher.getVersionByLocalFile(file, sha1)
                }
            }.getOrNull()
        }

        val result = select {
            modrinthDeferred.onAwait { result ->
                if (result != null) {
                    curseForgeDeferred.cancel()
                    result
                } else {
                    null
                }
            }
            curseForgeDeferred.onAwait { result ->
                if (result != null) {
                    modrinthDeferred.cancel()
                    result
                } else {
                    null
                }
            }
        }

        result ?: run {
            if (!modrinthDeferred.isCompleted) modrinthDeferred.await()
            else if (!curseForgeDeferred.isCompleted) curseForgeDeferred.await()
            else null
        }
    }
}

const val FINGERPRINT_BATCH_SIZE = 100

suspend fun getModrinthVersBySha1(
    sha1List: List<String>
): Map<String, ModrinthVersion> = mirroredPlatformSearcher(
    searchers = mirroredModrinthSource(),
    printLog = false
) { searcher ->
    searcher.getVersionFiles(sha1List = sha1List)
}

suspend fun getCFFilesByFingerprints(
    fingerprints: List<Long>
): Map<Long, CurseForgeFile> = mirroredPlatformSearcher(
    searchers = mirroredCurseForgeSource(),
    printLog = false
) { searcher ->
    searcher.getFilesByFingerprints(fingerprints = fingerprints)
}
