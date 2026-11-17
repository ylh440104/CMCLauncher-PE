package com.cmc.launcher.game.download.engine

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class BatchProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val downloadedFiles: Int,
    val totalFiles: Int,
    val speedBytesPerSec: Long
)

class DownloadStats {
    private val downloaded = AtomicLong(0L)
    private val totalBytesCounter = AtomicLong(-1L)
    private val totalFilesCounter = AtomicInteger(0)

    private val downloadedFilesCounter = AtomicInteger(0)
    val expectedTotalBytes: Long get() = totalBytesCounter.get()

    val totalFiles: Int get() = totalFilesCounter.get()
    val downloadedFiles: Int get() = downloadedFilesCounter.get()

    fun addBytes(count: Long) {
        downloaded.addAndGet(count)
    }

    fun registerFile(expectedSize: Long) {
        totalFilesCounter.incrementAndGet()
        registerBytes(expectedSize)
    }

    fun registerBytes(bytes: Long) {
        if (bytes > 0) {
            totalBytesCounter.accumulateAndGet(bytes) { previous, add ->
                if (previous < 0) add else previous + add
            }
        }
    }

    fun markFileFinished() {
        downloadedFilesCounter.incrementAndGet()
    }

    fun resetSpeedBaseline() {
        synchronized(this) {
            blockStartNanos = System.nanoTime()
            blockStartBytes = downloaded.get()
        }
    }

    val downloadedBytes: Long get() = downloaded.get()

    fun refreshSpeed(): Long {
        val now = System.nanoTime()
        synchronized(this) {
            val elapsed = now - blockStartNanos
            if (elapsed < SAMPLE_INTERVAL_NANOS) return currentSpeed

            val bytes = downloaded.get()
            currentSpeed = ((bytes - blockStartBytes) * NANOS_PER_SEC / elapsed)
                .coerceAtLeast(0L)
            blockStartNanos = now
            blockStartBytes = bytes
            return currentSpeed
        }
    }

    fun snapshotProgress(): BatchProgress = BatchProgress(
        downloadedBytes = downloaded.get(),
        totalBytes = expectedTotalBytes,
        downloadedFiles = downloadedFiles,
        totalFiles = totalFiles,
        speedBytesPerSec = refreshSpeed()
    )

    private var blockStartNanos = System.nanoTime()
    private var blockStartBytes = 0L

    private var currentSpeed: Long = 0L

    companion object {

        private const val SAMPLE_INTERVAL_NANOS = 1_000_000_000L
        private const val NANOS_PER_SEC = 1_000_000_000L
    }
}
