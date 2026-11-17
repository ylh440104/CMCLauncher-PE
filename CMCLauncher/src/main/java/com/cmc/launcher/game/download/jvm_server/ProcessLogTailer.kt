package com.cmc.launcher.game.download.jvm_server

import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ProcessLogTailer"

class ProcessLogTailer(
    private val logFile: File,
    private val output: TaskLogOutput,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL
) {

    fun launch(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {

        var offset = logFile.takeIf { it.exists() }?.length() ?: 0L
        var remainder = ByteArray(0)

        while (isActive) {
            delay(pollInterval)
            try {
                if (!logFile.exists()) continue
                val length = logFile.length()

                if (length < offset) {
                    offset = 0
                    remainder = ByteArray(0)
                }
                if (length <= offset) continue

                val bytes = RandomAccessFile(logFile, "r").use { raf ->
                    raf.seek(offset)
                    val buffer = ByteArray((length - offset).toInt())
                    raf.readFully(buffer)
                    buffer
                }
                offset = length

                val (completeLines, newRemainder) = splitCompleteLines(remainder + bytes)
                remainder = newRemainder
                if (remainder.size > MAX_PENDING_BYTES) remainder = ByteArray(0)
                if (completeLines.isNotEmpty()) output.appendLines(completeLines)
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to tail the installer process log", e)
            }
        }
    }

    private fun splitCompleteLines(bytes: ByteArray): Pair<List<String>, ByteArray> {
        val lastNewline = bytes.lastIndexOf('\n'.code.toByte())
        if (lastNewline < 0) return emptyList<String>() to bytes

        val completeText = String(bytes, 0, lastNewline + 1, StandardCharsets.UTF_8)
        val remainderBytes = bytes.copyOfRange(lastNewline + 1, bytes.size)

        val lines = completeText.split('\n')
            .map { it.removeSuffix("\r") }
            .filter { it.isNotEmpty() }
        return lines to remainderBytes
    }

    companion object {

        val DEFAULT_POLL_INTERVAL = 250.milliseconds

        private const val MAX_PENDING_BYTES = 1024 * 1024
    }
}
