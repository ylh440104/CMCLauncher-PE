package com.cmc.launcher.coroutine

import com.cmc.launcher.coroutine.TaskFlowExecutor.TaskPhase
import com.cmc.launcher.keepalive.TaskKeepAlive
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.isInterruptedIOException
import com.cmc.launcher.utils.string.getMessageOrToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TaskFlowExecutor"

class TaskFlowExecutor(
    private val scope: CoroutineScope
) {

    data class TaskPhase(
        val tasks: List<TitledTask>,
        val onComplete: (suspend () -> Unit)? = null
    )

    private val phases: MutableList<TaskPhase> = mutableListOf()

    private val _tasksFlow: MutableStateFlow<List<TitledTask>> = MutableStateFlow(emptyList())
    val tasksFlow = _tasksFlow.asStateFlow()

    private var job: Job? = null

    private var currentTaskJob: Job? = null

    private var currentPhaseIndex: Int = -1

    private fun getNextPhase(): TaskPhase? {
        currentPhaseIndex++
        return phases.getOrNull(currentPhaseIndex)
    }

    fun addPhase(phase: TaskPhase) {
        phases.add(phase)
    }

    fun addPhases(phases: List<TaskPhase>) {
        this.phases.addAll(phases)
    }

    suspend fun executePhases(
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onCancel: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        currentPhaseIndex = -1

        while (true) {
            try {
                ensureActive()
                val phase = getNextPhase() ?: break

                _tasksFlow.update { phase.tasks }

                for (task in phase.tasks) {
                    ensureActive()
                    task.task.updateStage(TaskStage.RUNNING)

                    val parentJob = coroutineContext[Job]
                    val taskJob = SupervisorJob(parentJob)
                    currentTaskJob = taskJob

                    try {
                        withContext(task.task.dispatcher + taskJob) {

                            coroutineScope {
                                try {

                                    task.task.task(this@coroutineScope, task.task)
                                } catch (e: CancellationException) {
                                    task.task.onCancel()
                                    throw e
                                } catch (e: Throwable) {
                                    task.task.onError(e)
                                    throw e
                                } finally {
                                    task.task.onFinally()
                                }
                            }
                        }
                        task.task.updateStage(TaskStage.COMPLETED)
                    } finally {

                        taskJob.cancel()
                        currentTaskJob = null
                    }
                }

                phase.onComplete?.invoke()
            } catch (th: Throwable) {
                if (th is CancellationException || th.isInterruptedIOException()) {
                    Logger.debug(TAG, "The current task flow has been cancelled. ${th.getMessageOrToString()}")
                    onCancel()
                } else {
                    Logger.warning(TAG, "An exception occurred while executing the task flow.", th)
                    onError(th)
                }
                return@withContext
            }
        }

        onComplete()
    }

    fun executePhasesAsync(
        onStart: suspend () -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        job = scope.launch(Dispatchers.IO) {

            TaskKeepAlive.acquire()
            try {
                onStart()
                executePhases(onComplete, onError, onCancel)
            } finally {
                TaskKeepAlive.release()
            }
        }
    }

    fun isRunning(): Boolean = job != null

    fun cancel() {

        currentTaskJob?.cancel()
        currentTaskJob = null

        job?.cancel()
        job = null

        _tasksFlow.update { emptyList() }
        currentPhaseIndex = -1
    }
}

fun buildPhase(
    onComplete: (suspend () -> Unit)? = null,
    builderAction: MutableList<TitledTask>.() -> Unit
): TaskPhase {
    val tasks: List<TitledTask> = buildList(builderAction = builderAction)
    return TaskPhase(
        tasks = tasks,
        onComplete = onComplete
    )
}
