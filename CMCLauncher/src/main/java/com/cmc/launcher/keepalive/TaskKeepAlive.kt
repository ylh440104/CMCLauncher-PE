package com.cmc.launcher.keepalive

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "TaskKeepAlive"

object TaskKeepAlive {

    private const val STOP_DELAY_MS = 1_500L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activeCount = AtomicInteger(0)
    private var stopJob: Job? = null
    private var isServiceForeground = false
    private var stopWhenForeground = false

    @Volatile
    private var appContext: Context? = null

    val count: Int get() = activeCount.get()

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    @Synchronized
    fun acquire() {
        val count = activeCount.incrementAndGet()

        stopJob?.cancel()
        stopJob = null

        if (count == 1) startService()
    }

    @JvmStatic
    @Synchronized
    fun release() {
        val count = activeCount.updateAndGet { if (it > 0) it - 1 else 0 }
        if (count == 0) scheduleStop()
    }

    @JvmStatic
    @Synchronized
    fun reset() {
        stopJob?.cancel()
        stopJob = null
        activeCount.set(0)
        stopService()
    }

    @Synchronized
    fun onServiceForegroundStarted() {
        isServiceForeground = true

        if (stopWhenForeground && activeCount.get() == 0) stopService()
    }

    private fun scheduleStop() {
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(STOP_DELAY_MS.milliseconds)

            if (activeCount.get() == 0) stopService()
        }
    }

    private fun startService() {
        val context = appContext ?: run {
            Logger.warning(TAG, "The application context is not initialized, skip starting the task keep-alive service.")
            return
        }

        val wasServiceForeground = isServiceForeground
        isServiceForeground = false
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TaskKeepAliveService::class.java)
            )
        }.onFailure { e ->
            isServiceForeground = wasServiceForeground

            Logger.error(TAG, "Failed to start the task keep-alive service", e)
        }
    }

    @Synchronized
    private fun stopService() {
        val context = appContext ?: return
        if (!isServiceForeground) {
            stopWhenForeground = true
            return
        }

        stopWhenForeground = false
        runCatching {
            context.stopService(Intent(context, TaskKeepAliveService::class.java))
        }.onSuccess {
            isServiceForeground = false
        }.onFailure { e ->
            Logger.error(TAG, "Failed to stop the task keep-alive service", e)
        }
    }
}
