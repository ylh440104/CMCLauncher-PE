package com.cmc.launcher.game.control

import com.movtery.layer_controller.observable.ObservableControlLayout
import java.io.File

data class ControlData(
    val file: File,
    val controlLayout: ObservableControlLayout,
    val isSupport: Boolean = true
)
