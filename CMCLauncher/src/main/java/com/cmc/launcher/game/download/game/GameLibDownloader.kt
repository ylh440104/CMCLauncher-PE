package com.cmc.launcher.game.download.game

import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.version.download.BaseMinecraftDownloader
import com.cmc.launcher.game.version.download.DEFAULT_DOWNLOAD_THREADS
import com.cmc.launcher.game.version.download.DownloadTask
import com.cmc.launcher.game.version.download.parseTo
import com.cmc.launcher.game.version.download.runBatchDownloads
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.formatFileSize
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class GameLibDownloader(
    private val downloader: BaseMinecraftDownloader,
    private val gameJson: String,
    private val maxDownloadThreads: Int = DEFAULT_DOWNLOAD_THREADS
) {
    private val allDownloadTasks = ConcurrentLinkedQueue<DownloadTask>()

    private var isDownloadStarted: Boolean = false

    suspend fun schedule(
        task: Task,
        targetDir: File = downloader.librariesTarget,
        updateProgress: Boolean = true
    ) {
        val gameManifest = gameJson.parseTo(GameManifest::class.java)

        if (updateProgress) {
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.minecraft_download_stat_download_task))
        }

        downloader.loadLibraryDownloads(gameManifest, targetDir) { urls, hash, targetFile, size, isDownloadable ->
            scheduleDownload(urls, hash, targetFile, size, isDownloadable)
        }
    }

    suspend fun download(task: Task) {
        isDownloadStarted = true
        val tasks = allDownloadTasks.toList()
        if (tasks.isNotEmpty()) {
            task.runBatchDownloads(
                tasks = tasks,
                maxConnections = maxDownloadThreads,
                retryRounds = 1,
                onSnapshot = { snapshot ->
                    task.updateSpeed(snapshot.speedBytesPerSec)
                    task.updateMessage(androidText(
                        R.string.minecraft_download_downloading_game_files,
                        snapshot.downloadedFiles, snapshot.totalFiles,
                        formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                    ))
                }
            )
        }

        task.updateProgress(1f)
        task.updateMessage(null)
    }

    fun scheduleDownload(urls: List<String>, sha1: String?, targetFile: File, size: Long, isDownloadable: Boolean = true) {
        if (isDownloadStarted) throw IllegalStateException("The download has already started; adding more download tasks is no longer meaningful.")

        if (allDownloadTasks.any { it.targetFile.absolutePath == targetFile.absolutePath }) return

        allDownloadTasks.add(
            DownloadTask(
                urls = urls,
                verifyIntegrity = true,
                targetFile = targetFile,
                sha1 = sha1,
                size = size,
                isDownloadable = isDownloadable
            )
        )
    }

    fun removeDownload(predicate: (DownloadTask) -> Boolean) {
        if (isDownloadStarted) throw IllegalStateException("The download has already started; removing download tasks is no longer meaningful.")
        allDownloadTasks.removeIf(predicate)
    }
}
