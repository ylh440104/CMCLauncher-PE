package com.cmc.launcher.game.download.modpack.install

import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.download.engine.findHttpCode
import com.cmc.launcher.game.version.download.DownloadFailedException
import com.cmc.launcher.game.version.download.DownloadTask
import com.cmc.launcher.game.version.download.runBatchDownloads
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.formatFileSize
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.isInterruptedIOException
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ModDownloader"

class ModDownloader(
    val mods: List<ModFile>,
    private val maxDownloadThreads: Int = 64
) {
    suspend fun startDownload(task: Task) {
        task.updateMessage(null)

        val resolvedFailures = AtomicInteger(0)
        val missingMods = AtomicInteger(0)
        val skippedMods = ConcurrentHashMap.newKeySet<String>()
        val tasks = prepareAll(resolvedFailures, missingMods)

        try {
            runBatch(task, tasks, R.string.download_modpack_download_mods, missingMods, skippedMods)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadFailedException) {

            Logger.error(TAG, "Some mods failed to download, retrying failed mods", e)
            runBatch(task, tasks, R.string.download_modpack_download_mods_retry, missingMods, skippedMods)
        }

        if (resolvedFailures.get() > 0) {
            throw DownloadFailedException("${resolvedFailures.get()} mods failed to resolve download links")
        }

        task.updateProgress(1f)
        task.updateMessage(null)
    }

    private suspend fun runBatch(
        task: Task,
        tasks: List<DownloadTask>,
        messageRes: Int,
        missingMods: AtomicInteger,
        skippedMods: MutableSet<String>
    ) {
        task.runBatchDownloads(
            tasks = tasks,
            maxConnections = maxDownloadThreads,
            retryRounds = 1,
            onSnapshot = { snapshot ->
                task.updateSpeed(snapshot.speedBytesPerSec)
                task.updateMessage(
                    androidText(
                        messageRes,
                        snapshot.downloadedFiles + missingMods.get(),
                        snapshot.totalFiles + missingMods.get(),
                        formatFileSize(snapshot.downloadedBytes)
                    )
                )
            },
            acceptFailure = { downloadTask, error ->
                val code = error.findHttpCode()
                val skipped = error is FileNotFoundException || error is NotFoundException || code == 404

                if (skipped && skippedMods.add(downloadTask.targetFile.absolutePath)) {
                    missingMods.incrementAndGet()
                }
                skipped
            }
        )
    }

    private suspend fun prepareAll(resolvedFailures: AtomicInteger, missingMods: AtomicInteger): List<DownloadTask> =
        coroutineScope {
            val semaphore = Semaphore(maxDownloadThreads)
            mods.map { mod ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            prepare(mod)
                        } catch (_: CancellationException) {
                            throw CancellationException("mod link resolving cancelled")
                        } catch (_: FileNotFoundException) {

                            missingMods.incrementAndGet()
                            null
                        } catch (_: NotFoundException) {

                            missingMods.incrementAndGet()
                            null
                        } catch (e: Exception) {
                            if (e.isInterruptedIOException()) throw CancellationException("cancelled", e)
                            Logger.error(TAG, "Failed to resolve mod links: ${mod.outputFile?.name}", e)
                            resolvedFailures.incrementAndGet()
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

    private suspend fun prepare(mod: ModFile): DownloadTask {
        val file = mod.getFile?.invoke() ?: mod
        val urls = file.downloadUrls ?: throw FileNotFoundException("No download url for ${file.outputFile}")
        val output = file.outputFile ?: throw FileNotFoundException("No target file for mod")

        return DownloadTask(
            urls = urls,
            verifyIntegrity = true,
            targetFile = output,
            sha1 = file.sha1
        )
    }
}
