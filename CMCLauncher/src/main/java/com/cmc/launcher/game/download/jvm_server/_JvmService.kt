package com.cmc.launcher.game.download.jvm_server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.cmc.launcher.notification.NoticeProgress
import com.cmc.launcher.path.PathManager
import java.io.File

const val PROCESS_SERVICE_PORT = 53151

val LATEST_PROCESS_LOG_FILE: File get() = File(PathManager.DIR_FILES_EXTERNAL, "latest_process.log")

const val SERVICE_JVM_ARGS = "service.jvm.args"
const val SERVICE_JRE_NAME = "service.jre.name"
const val SERVICE_USER_HOME = "service.user.home"
const val SERVICE_POST_SUMMARY = "service.post.summary"
const val SERVICE_POST_PROGRESS = "service.post.progress"

fun startJvmService(
    context: Context,
    jvmArgs: String,
    jreName: String? = null,
    userHome: String? = null,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
) {
    val bundle = Bundle().apply {
        putString(SERVICE_JVM_ARGS, jvmArgs)
        putString(SERVICE_JRE_NAME, jreName)
        putString(SERVICE_USER_HOME, userHome)
        putString(SERVICE_POST_SUMMARY, postSummary)
        putParcelable(SERVICE_POST_PROGRESS, postProgress)
    }
    val intent = Intent(context, JvmService::class.java).apply {
        putExtras(bundle)
    }
    context.startForegroundService(intent)
}

fun Throwable.isProcessStartRefused(): Boolean =
    this is SecurityException && message?.contains("process is bad") == true

private val JVM_EXCLUSIVE_SUFFIXES = listOf(":jvm", ":game")

internal fun isJvmExclusiveProcess(processName: String, mainProcessName: String): Boolean =
    JVM_EXCLUSIVE_SUFFIXES.any { processName == mainProcessName + it }

fun listBlockingProcesses(context: Context): List<String> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mainProcessName = context.packageName
    val myPid = android.os.Process.myPid()

    return am.runningAppProcesses
        .filter { it.pid != myPid && isJvmExclusiveProcess(it.processName, mainProcessName) }
        .map { "${it.processName}(${it.pid})" }
}

fun stopAllNonMainProcesses(context: Context) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mainPid = android.os.Process.myPid()
    val mainProcessName = context.packageName

    am.runningAppProcesses
        .filter { it.pid != mainPid && isJvmExclusiveProcess(it.processName, mainProcessName) }
        .forEach {
            try {
                android.os.Process.killProcess(it.pid)
            } catch (_: Exception) {

            }
        }
}
