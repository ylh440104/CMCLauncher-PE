package com.cmc.launcher.game.download.modpack.platform

import android.content.Context
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TaskLogOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

abstract class AbstractPack(
    val platform: PackPlatform
) {

    abstract fun getFinalClientName(): String

    abstract fun buildTaskPhases(
        context: Context,
        scope: CoroutineScope,
        versionFolder: File,
        waitForVersionName: suspend (name: String) -> String,
        addPhases: (List<TaskFlowExecutor.TaskPhase>) -> Unit,
        onClearTemp: suspend () -> Unit,
        logOutputHolder: MutableStateFlow<TaskLogOutput?> = MutableStateFlow(null)
    ): List<TaskFlowExecutor.TaskPhase>
}
