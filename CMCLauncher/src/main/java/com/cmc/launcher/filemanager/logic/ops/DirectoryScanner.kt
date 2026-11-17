package com.cmc.launcher.filemanager.logic.ops

import com.cmc.launcher.filemanager.os.FmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque

private const val TAG = "FmDirScanner"

data class DirStats(
    val totalSize: Long = 0L,
    val fileCount: Long = 0L,
    val dirCount: Long = 0L
)

object DirectoryScanner {

    suspend fun scan(
        root: Path,
        onProgress: ((stats: DirStats, current: Path?) -> Unit)? = null,
        checkCancelled: () -> Boolean = { false }
    ): DirStats = withContext(Dispatchers.IO) {
        var stats = DirStats()
        val stack = ArrayDeque<Path>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            if (checkCancelled()) throw CancellationException("Directory scan cancelled")
            val dir = stack.pop()

            runCatching {
                Files.newDirectoryStream(dir).use { entries ->
                    for (child in entries) {
                        if (checkCancelled()) throw CancellationException("Directory scan cancelled")
                        try {
                            val attrs = Files.readAttributes(
                                child,
                                BasicFileAttributes::class.java,
                                LinkOption.NOFOLLOW_LINKS
                            )
                            if (attrs.isSymbolicLink) {

                                FmLog.debug(TAG, "Skip symlink during scan: $child")
                                continue
                            }
                            if (attrs.isDirectory) {
                                stats = stats.copy(dirCount = stats.dirCount + 1)
                                stack.push(child)
                            } else if (attrs.isRegularFile) {
                                stats = stats.copy(
                                    fileCount = stats.fileCount + 1,
                                    totalSize = stats.totalSize + attrs.size()
                                )
                            }
                        } catch (e: Exception) {
                            FmLog.warn(TAG, "Failed to stat: $child", e)

                        }
                    }
                }
            }.onFailure { e ->
                FmLog.warn(TAG, "Failed to read directory: $dir", e)
            }

            onProgress?.invoke(stats, dir)
        }

        stats
    }
}
