package com.cmc.launcher.coroutine

import com.cmc.launcher.ui.AndroidStringText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class Task private constructor(
    val id: String,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val task: suspend CoroutineScope.(Task) -> Unit,
    val onError: suspend (Throwable) -> Unit = {},
    val onFinally: () -> Unit = {},
    val onCancel: () -> Unit = {}
) {
    private val _stage = MutableStateFlow(TaskStage.PREPARING)

    val stage = _stage.asStateFlow()

    private val _progress = MutableStateFlow(-1f)

    val progress = _progress.asStateFlow()

    private val _message = MutableStateFlow<AndroidStringText?>(null)

    val message = _message.asStateFlow()

    private val _rateBytesPerSec = MutableStateFlow<Long?>(null)

    val rateBytesPerSec = _rateBytesPerSec.asStateFlow()

    fun updateStage(state: TaskStage) {
        this._stage.update { state }
    }

    fun updateProgress(percentage: Float) {
        this._progress.update {
            (percentage.takeIf { it.isFinite() } ?: 0f).coerceIn(-1f, 1f)
        }
    }

    fun updateMessage(text: AndroidStringText?) {
        this._message.update { text }
    }

    fun updateSpeed(bytes: Long) {
        this._rateBytesPerSec.update { bytes.takeIf { it >= 0L } }
    }

    fun clearSpeed() {
        this._rateBytesPerSec.update { null }
    }

    override fun equals(other: Any?): Boolean = other is Task && other.id == this.id

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun runTask(
            id: String? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            task: suspend CoroutineScope.(Task) -> Unit,
            onError: suspend (Throwable) -> Unit = {},
            onFinally: () -> Unit = {},
            onCancel: () -> Unit = {}
        ): Task =
            Task(
                id = id ?: getRandomID(),
                dispatcher = dispatcher,
                task = task,
                onError = onError,
                onFinally = onFinally,
                onCancel = onCancel
            )

        private fun getRandomID(): String = UUID.randomUUID().toString()
    }
}
