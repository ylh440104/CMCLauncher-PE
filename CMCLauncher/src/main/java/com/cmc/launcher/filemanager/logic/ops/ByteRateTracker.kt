package com.cmc.launcher.filemanager.logic.ops

private const val WINDOW_NANOS = 500_000_000L

class ByteRateTracker {
    private var lastSampleNanos = 0L
    private var lastBytes = 0L
    private var currentRate = 0L

    fun start() {
        lastSampleNanos = System.nanoTime()
        lastBytes = 0L
        currentRate = 0L
    }

    fun rate(cumulativeBytes: Long): Long {
        val now = System.nanoTime()
        val elapsed = now - lastSampleNanos
        if (elapsed >= WINDOW_NANOS) {
            val delta = cumulativeBytes - lastBytes
            if (delta >= 0) {
                currentRate = (delta * 1_000_000_000L) / elapsed
            }
            lastSampleNanos = now
            lastBytes = cumulativeBytes
        }
        return currentRate
    }
}
