package com.cmc.launcher.game.version.download

import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.download.engine.BatchDownloader
import com.cmc.launcher.game.download.engine.BatchProgress
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private const val TAG = "TaskBatch"

private const val LOCAL_VERIFY_PARALLELISM = 4

suspend fun Task.runBatchDownloads(
    tasks: List<DownloadTask>,
    maxConnections: Int,
    retryRounds: Int = 1,
    onSnapshot: suspend (BatchProgress) -> Unit = {},
    acceptFailure: ((task: DownloadTask, error: Throwable) -> Boolean)? = null
) {
    val verifyStarted = System.currentTimeMillis()
    val (reusable, pending) = verifyExistingFilesConcurrently(tasks)
    Logger.info(TAG, "Local file check done: reusable=${reusable.size} pending=${pending.size}, took=${System.currentTimeMillis() - verifyStarted}ms")

    val sizesFullyKnown = tasks.all { it.size > 0 }

    val batch = BatchDownloader(
        requests = pending.map { it.toRequest() },
        maxConnections = maxConnections,
        retryRounds = retryRounds
    )

    reusable.forEach {
        batch.stats.registerFile(it.size)
        batch.stats.markFileFinished()
        it.runFileDownloadedTask()
    }

    if (reusable.isNotEmpty()) {
        batch.stats.addBytes(reusable.sumOf { maxOf(it.size, 0L) })

        batch.stats.resetSpeedBaseline()
    }

    batch.onUpdate = { snapshot ->
        updateProgress(progressFor(snapshot, sizesFullyKnown, hasFileCount = snapshot.totalFiles > 0))
        onSnapshot(snapshot)
    }
    batch.onFileSuccess = { request ->
        (request.tag as? DownloadTask)?.runFileDownloadedTask()
    }
    acceptFailure?.let { judge ->
        batch.onFailureFilter = { request, error ->
            (request.tag as? DownloadTask)?.let { judge(it, error) } ?: false
        }
    }

    try {
        batch.run()
        updateProgress(1f)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val failedDetail = batch.lastRunFailures.keys.joinToString("\n") { path -> path }
        throw DownloadFailedException("Failed downloads:\n$failedDetail", e)
    } finally {
        clearSpeed()
    }
}

private suspend fun verifyExistingFilesConcurrently(
    tasks: List<DownloadTask>
): Pair<List<DownloadTask>, List<DownloadTask>> =
    withContext(Dispatchers.IO.limitedParallelism(LOCAL_VERIFY_PARALLELISM)) {
        coroutineScope {
            tasks.map { task ->
                async { task to task.existingFileValid() }
            }.awaitAll()
        }.partition { it.second }
            .let { (reusable, pending) -> reusable.map { it.first } to pending.map { it.first } }
    }

private fun progressFor(snapshot: BatchProgress, sizesFullyKnown: Boolean, hasFileCount: Boolean): Float = when {
    sizesFullyKnown && snapshot.totalBytes > 0 ->
        (snapshot.downloadedBytes.toFloat() / snapshot.totalBytes).coerceIn(0f, 1f)

    hasFileCount && snapshot.totalFiles > 0 ->
        snapshot.downloadedFiles.toFloat() / snapshot.totalFiles

    else -> -1f
}
