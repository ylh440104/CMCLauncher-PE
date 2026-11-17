package com.cmc.launcher.game.download.jvm_server

import android.content.Intent
import com.cmc.launcher.components.jre.Jre
import com.cmc.launcher.context.GlobalContext
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.notification.NoticeProgress
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "GameJVMRunner"

private val WAIT_BEFORE_FORCE_KILL: Duration = 15.seconds

private val KILL_GRACE: Duration = 5.seconds

private val WAIT_LOG_INTERVAL: Duration = 2.seconds

private val JVM_EXIT_TIMEOUT: Duration = 15.minutes

private const val SERVICE_START_RETRY_MAX = 4

private val SERVICE_START_RETRY_DELAY: Duration = 500.milliseconds

private val POST_PROCESS_EXIT_COOLDOWN: Duration = 1.seconds

suspend fun runJvmRetryRuntimes(
    logId: String,
    jvmArgs: String,
    prefixArgs: (Jre) -> String?,
    jre: Jre,
    userHome: String,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
    start: () -> Unit = {},
    logOutput: TaskLogOutput? = null
): Unit = withContext(Dispatchers.Default) {
    waitForJvmExclusiveProcessesStopped(logId)

    start()

    val finalArgs = prefixArgs(jre)?.let {
        "$it $jvmArgs"
    } ?: jvmArgs

    val exitCode = startJvmServiceAndWaitExit(
        jvmArgs = finalArgs,
        jreName = jre.jreName,
        userHome = userHome,
        postSummary = postSummary,
        postProgress = postProgress,
        logOutput = logOutput
    )

    if (exitCode != 0) {
        val nextJava: Jre? = when (jre) {
            Jre.JRE_8 -> Jre.JRE_17
            Jre.JRE_17 -> Jre.JRE_21
            else -> null
        }

        nextJava?.let { jre ->
            Logger.info(TAG, "Retry with jre ${jre.name}...")
            runJvmRetryRuntimes(
                logId = logId,
                jvmArgs = jvmArgs,
                prefixArgs = prefixArgs,
                jre = jre,
                userHome = userHome,
                postSummary = postSummary,
                postProgress = postProgress,
                logOutput = logOutput
            )
        } ?: throw JvmCrashException(exitCode)
    }
}

private suspend fun waitForJvmExclusiveProcessesStopped(logId: String) {
    val startNanos = System.nanoTime()
    var lastLog = -WAIT_LOG_INTERVAL
    var forceKilled = false
    var sawBlocking = false

    while (true) {
        val blocking = listBlockingProcesses(GlobalContext)
        if (blocking.isEmpty()) {
            if (!sawBlocking) {

                return
            }

            delay(POST_PROCESS_EXIT_COOLDOWN)
            if (listBlockingProcesses(GlobalContext).isEmpty()) return
            continue
        }

        sawBlocking = true

        val elapsed = (System.nanoTime() - startNanos).nanoseconds
        when {
            elapsed >= WAIT_BEFORE_FORCE_KILL + KILL_GRACE ->
                throw IOException(
                    "Timed out waiting for exclusive processes to stop: ${blocking.joinToString()}"
                )

            elapsed >= WAIT_BEFORE_FORCE_KILL -> {
                if (!forceKilled) {
                    forceKilled = true
                    Logger.warning(TAG, "$logId Force stopping blocking processes: $blocking")
                }
                stopAllNonMainProcesses(GlobalContext)
            }

            elapsed - lastLog >= WAIT_LOG_INTERVAL -> {
                lastLog = elapsed
                Logger.info(TAG, "$logId Waiting for other processes stop... [$blocking]")
            }
        }
        delay(100L.milliseconds)
    }
}

suspend fun startJvmServiceAndWaitExit(
    jvmArgs: String,
    jreName: String? = null,
    userHome: String? = null,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
    logOutput: TaskLogOutput? = null,
): Int = withContext(Dispatchers.IO) {
    val doneSignal = CompletableDeferred<Unit>()

    val tailerJob = logOutput?.let {
        ProcessLogTailer(LATEST_PROCESS_LOG_FILE, it).launch(this)
    }

    try {

        JVMSocketServer.start { receiveMsg ->
            Logger.info(TAG, "receive msg: $receiveMsg, stopping server...")
            if (!doneSignal.isCompleted) {
                doneSignal.complete(Unit)
            }
            JVMSocketServer.stop()
        }

        var attempt = 0
        while (true) {
            try {
                startJvmService(
                    context = GlobalContext,
                    jvmArgs = jvmArgs,
                    userHome = userHome,
                    jreName = jreName,
                    postSummary = postSummary,
                    postProgress = postProgress
                )
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                attempt++
                if (attempt > SERVICE_START_RETRY_MAX) {
                    Logger.error(TAG, "Failed to start JvmService after $attempt attempts", e)
                    throw e
                }
                val retryDelay = SERVICE_START_RETRY_DELAY * (1 shl (attempt - 1))
                Logger.warning(
                    TAG,
                    "Failed to start JvmService (attempt $attempt/$SERVICE_START_RETRY_MAX): " +
                        "${e.message}. Retrying in $retryDelay..."
                )
                delay(retryDelay)
            }
        }

        if (withTimeoutOrNull(JVM_EXIT_TIMEOUT) { doneSignal.await() } == null) {

            Logger.error(TAG, "Timed out ($JVM_EXIT_TIMEOUT) waiting for JVM exit code, stopping service...")
            runCatching {
                GlobalContext.stopService(Intent(GlobalContext, JvmService::class.java))
            }
            throw IOException("Timed out waiting for the JVM process to exit.")
        }
    } finally {

        JVMSocketServer.stop()
        tailerJob?.cancel()
    }

    val code = JVMSocketServer.receiveMsg?.toIntOrNull()
    Logger.info(TAG, "receive exit code: ${code ?: "unknown, default 0"}")
    code ?: 0
}
