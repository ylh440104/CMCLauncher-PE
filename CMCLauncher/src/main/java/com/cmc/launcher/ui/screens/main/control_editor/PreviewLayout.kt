package com.cmc.launcher.ui.screens.main.control_editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.IntSize
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.cmc.launcher.setting.enums.isLauncherInDarkTheme
import com.cmc.launcher.ui.components.rememberBoxSize
import com.cmc.launcher.ui.control.mouse.SwitchableMouseLayout

@Composable
fun BoxWithConstraintsScope.PreviewControlBox(
    observableLayout: ObservableControlLayout,
    previewScenario: PreviewScenario,
    previewHideLayerWhen: HideLayerWhen,
    modifier: Modifier = Modifier,
) {
    val occupiedPointers = remember(observableLayout) { mutableStateSetOf<PointerId>() }
    val moveOnlyPointers = remember(observableLayout) { mutableStateSetOf<PointerId>() }

    val screenSize = rememberBoxSize()

    ControlBoxLayout(
        modifier = modifier.fillMaxSize(),
        observedLayout = observableLayout,
        checkOccupiedPointers = { occupiedPointers.contains(it) },
        markPointerAsMoveOnly = { moveOnlyPointers.add(it) },
        onOccupiedPointer = { occupiedPointers.add(it) },
        onReleasePointer = { occupiedPointers.remove(it) },
        isCursorGrabbing = previewScenario.isCursorGrabbing,
        hideLayerWhen = previewHideLayerWhen,
        isDark = isLauncherInDarkTheme()
    ) {
        PreviewMouseLayout(
            modifier = Modifier.fillMaxSize(),
            screenSize = screenSize,
            isMoveOnlyPointer = { moveOnlyPointers.contains(it) },
            onOccupiedPointer = { occupiedPointers.add(it) },
            onReleasePointer = {
                occupiedPointers.remove(it)
                moveOnlyPointers.remove(it)
            },
            previewScenario = previewScenario
        )
    }
}

@Composable
private fun PreviewMouseLayout(
    modifier: Modifier = Modifier,
    screenSize: IntSize,
    isMoveOnlyPointer: (PointerId) -> Boolean,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit,
    previewScenario: PreviewScenario
) {
    Box(
        modifier = modifier
    ) {
        SwitchableMouseLayout(
            modifier = Modifier.fillMaxSize(),
            screenSize = screenSize,
            cursorMode = previewScenario.cursorMode,
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer
        )
    }
}
