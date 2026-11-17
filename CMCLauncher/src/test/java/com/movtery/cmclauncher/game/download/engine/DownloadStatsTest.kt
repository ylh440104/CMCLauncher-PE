package com.movtery.cmclauncher.game.download.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStatsTest {

    @Test
    fun `concurrent counters conserve every increment`() = runBlocking<Unit> {
        val stats = DownloadStats()
        val workers = 64
        val perWorker = 40

        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.Default) {
                    repeat(perWorker) {
                        stats.markFileFinished()
                        stats.registerFile(10L)
                    }
                }
            }
        }

        assertEquals(workers * perWorker, stats.downloadedFiles)
        assertEquals(workers * perWorker, stats.totalFiles)
        assertEquals(workers.toLong() * perWorker * 10L, stats.expectedTotalBytes)
    }

    @Test
    fun `seeded bytes stay out of the speed meter after baseline reset`() = runBlocking<Unit> {
        val stats = DownloadStats()
        stats.registerFile(1_000L)
        stats.markFileFinished()

        stats.addBytes(1_000L)
        stats.resetSpeedBaseline()

        delay(1_100)
        assertEquals(0L, stats.refreshSpeed())
        assertEquals(1_000L, stats.downloadedBytes)
        assertEquals(1_000L, stats.expectedTotalBytes)
    }
}
