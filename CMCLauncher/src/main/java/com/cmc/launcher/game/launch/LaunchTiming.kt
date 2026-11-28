package com.cmc.launcher.game.launch

import android.util.Log

object LaunchTiming {

    private const val TAG = "LaunchTiming"

    @Volatile
    private var startedAt = 0L

    fun start() {
        startedAt = System.nanoTime()
        mark("begin")
    }

    fun mark(label: String) {
        val start = startedAt
        if (start == 0L) return
        val elapsed = (System.nanoTime() - start) / 1_000_000L
        Log.i(TAG, "$label=${elapsed}ms")
    }
}