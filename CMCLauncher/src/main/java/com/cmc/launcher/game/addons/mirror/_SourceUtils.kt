package com.cmc.launcher.game.addons.mirror

import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

private const val TAG = "SourceUtils"

data class MirrorSource<T>(
    val type: SourceType,
    val block: suspend () -> T?
)

suspend fun <T> runMirrorable(
    sources: List<MirrorSource<T>>
): T? = withContext(Dispatchers.IO) {
    var result: T? = null
    var succeed = false
    var lastException: Throwable? = null

    loop@ for (source in sources) {
        ensureActive()

        runCatching {
            val res = source.block()
            result = res
            succeed = true
            break@loop
        }.onFailure {
            Logger.debug(TAG, "Source ${source.type.displayName} failed!", it)
            lastException = it
        }
    }

    if (!succeed) throw lastException ?: IOException("Failed to retrieve information from the source!")

    result
}

fun <T> List<MirrorSource<T>>.orderedByGameSourcePreference(): List<MirrorSource<T>> =
    when (resolveMirrorPriority(AllSettings.gameDownloadSource.getValue(), mainland = true)) {
        MirrorPriority.OFFICIAL -> filter { it.type == SourceType.OFFICIAL }
        MirrorPriority.MIRROR_FIRST -> reversed()
    }
