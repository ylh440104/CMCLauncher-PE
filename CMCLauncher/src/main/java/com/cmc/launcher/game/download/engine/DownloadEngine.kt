package com.cmc.launcher.game.download.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

object DownloadEngine {

    suspend fun download(
        request: DownloadRequest,
        stats: DownloadStats = DownloadStats(),
        sizeCallback: (Long) -> Unit = {}
    ) {
        if (request.expectedSize > 0) stats.registerFile(request.expectedSize)

        if (sizeCallback === defaultCallback) {
            FileDownloader(request, stats).download()
            stats.markFileFinished()
            return
        }

        val reported = AtomicLong(0L)
        coroutineScope {
            val reporter = launch(Dispatchers.Default) {
                while (isActive) {
                    delay(PROGRESS_SAMPLE_MS.milliseconds)
                    drain(reported, stats, sizeCallback)
                }
            }
            try {
                FileDownloader(request, stats).download()
                stats.markFileFinished()
                drain(reported, stats, sizeCallback)
            } finally {
                reporter.cancelAndJoin()
            }
        }
    }

    private fun drain(reported: AtomicLong, stats: DownloadStats, callback: (Long) -> Unit) {
        val total = stats.downloadedBytes
        val delta = total - reported.get()
        if (delta != 0L) {
            reported.addAndGet(delta)
            callback(delta)
        }
    }

    private val defaultCallback: (Long) -> Unit = {}

    private const val PROGRESS_SAMPLE_MS = 100L
}
