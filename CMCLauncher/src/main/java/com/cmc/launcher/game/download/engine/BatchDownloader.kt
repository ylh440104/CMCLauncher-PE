package com.cmc.launcher.game.download.engine

import com.cmc.launcher.game.download.engine.BatchDownloader.Companion.PROGRESS_INTERVAL_MS
import com.cmc.launcher.game.download.engine.BatchDownloader.Companion.SYSTEMIC_FAILURE_LIMIT
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class BatchDownloadException(summary: String, cause: Throwable? = null) : IOException(summary, cause)

class BatchDownloader(
    private val requests: List<DownloadRequest>,
    private val maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    private val retryRounds: Int = 1
) {
    val stats = DownloadStats()

    var onUpdate: (suspend (BatchProgress) -> Unit)? = null

    var onFileSuccess: (suspend (DownloadRequest) -> Unit)? = null

    var onFailureFilter: ((DownloadRequest, Throwable) -> Boolean)? = null

    private val files = Semaphore(maxConnections)

    private val systemicFailureCount = AtomicInteger(0)

    private val systemicAbortCause = AtomicReference<Throwable?>(null)

    private val activeFileJobs = AtomicReference<List<Job>?>(null)

    var lastRunFailures: Map<String, Throwable> = emptyMap()
        private set

    suspend fun run() {

        requests.forEach { stats.registerFile(it.expectedSize) }
        systemicFailureCount.set(0)
        systemicAbortCause.set(null)

        val failures = ConcurrentHashMap<String, Throwable>()
        coroutineScope {
            val reporter = launch(Dispatchers.Default) {
                while (isActive) {
                    delay(PROGRESS_INTERVAL_MS.milliseconds)
                    onUpdate?.invoke(stats.snapshotProgress())
                }
            }

            try {
                val fileJobs = requests.map { request ->
                    launch(Dispatchers.IO) {

                        files.withPermit {
                            runOne(request, failures)
                        }
                    }
                }
                activeFileJobs.set(fileJobs)
                fileJobs.joinAll()
            } finally {
                activeFileJobs.set(null)
                reporter.cancelAndJoin()
            }
        }

        systemicAbortCause.get()?.let { cause ->
            lastRunFailures = failures.toMap()
            throw BatchDownloadException(
                "Batch aborted after $SYSTEMIC_FAILURE_LIMIT consecutive permanent failures " +
                        "with no successful download; the failure looks systemic rather than per-file",
                cause
            )
        }

        val finished = stats.downloadedFiles
        if (finished != requests.size) {
            val failedCount = failures.size
            val outcome = if (failedCount == 0) "all" else "$failedCount failed"
            Logger.warning(TAG, "Completed-count mismatch: requests=" + requests.size
                    + " finished=" + finished + " failures=" + outcome)
        }

        if (failures.isNotEmpty()) {
            lastRunFailures = failures.toMap()
            val detail = failures.entries.joinToString(separator = "\n") { (path, error) ->
                "$path: ${error.message ?: error::class.simpleName}"
            }

            val summaryLines = detail.lines()
            val summary = if (summaryLines.size > MAX_FAILURE_DETAIL_LINES) {
                summaryLines.take(MAX_FAILURE_DETAIL_LINES).joinToString("\n") +
                        "\n... and ${summaryLines.size - MAX_FAILURE_DETAIL_LINES} more lines"
            } else {
                detail
            }
            throw BatchDownloadException(summary)
        }
    }

    private suspend fun runOne(
        request: DownloadRequest,
        failures: MutableMap<String, Throwable>
    ) {
        var lastError: Throwable? = null
        repeat(retryRounds + 1) {
            try {
                FileDownloader(request, stats).download()
                onFileSuccess?.invoke(request)
                stats.markFileFinished()
                systemicFailureCount.set(0)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        lastError?.let { error ->
            Logger.error(TAG, "Download failed permanently: ${request.targetFile.absolutePath}", error)
            if (onFailureFilter?.invoke(request, error) == true) {
                stats.markFileFinished()
                systemicFailureCount.set(0)
                return
            }
            failures[request.targetFile.absolutePath] = error

            if (stats.downloadedFiles == 0) {
                val count = systemicFailureCount.incrementAndGet()
                if (count >= SYSTEMIC_FAILURE_LIMIT && systemicAbortCause.compareAndSet(null, error)) {
                    Logger.warning(TAG, "Aborting batch: $count consecutive permanent failures with zero successful downloads", error)
                    activeFileJobs.get()?.forEach { it.cancel() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BatchDownloader"
        const val DEFAULT_MAX_CONNECTIONS = 64
        const val PROGRESS_INTERVAL_MS = 100L

        private const val MAX_FAILURE_DETAIL_LINES = 20

        const val SYSTEMIC_FAILURE_LIMIT = 5
    }
}
