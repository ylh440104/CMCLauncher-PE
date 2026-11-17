package com.cmc.launcher.coroutine

import androidx.annotation.Keep
import com.cmc.launcher.ui.AndroidStringText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Keep
class TaskLogOutput(
    private val holder: MutableStateFlow<TaskLogOutput?>,
    val title: AndroidStringText,
    private val maxLines: Int = DEFAULT_MAX_LINES
) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _active = MutableStateFlow(false)

    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var published = false

    fun start() {
        _lines.update { emptyList() }
        _active.update { true }
    }

    fun stop() {
        _active.update { false }
    }

    fun appendLine(line: String) {
        appendLines(listOf(line))
    }

    fun appendLines(lines: List<String>) {
        if (lines.isEmpty()) return
        _lines.update { current -> (current + lines).takeLast(maxLines) }
        publishIfNeeded()
    }

    private fun publishIfNeeded() {
        if (published || _lines.value.isEmpty()) return
        published = true
        holder.value = this
    }

    companion object {

        const val DEFAULT_MAX_LINES = 256
    }
}

suspend fun <R> withTaskLogOutput(
    holder: MutableStateFlow<TaskLogOutput?>,
    title: AndroidStringText,
    block: suspend (TaskLogOutput) -> R
): R {
    val output = TaskLogOutput(holder, title).also { it.start() }
    try {
        return block(output)
    } finally {
        output.stop()
        holder.compareAndSet(output, null)
    }
}
