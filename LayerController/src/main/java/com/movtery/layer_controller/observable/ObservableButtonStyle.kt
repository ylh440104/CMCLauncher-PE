package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.DefaultButtonStyle
import com.movtery.layer_controller.data.cloneNew

class ObservableButtonStyle(
    private val style: ButtonStyle
): Packable<ButtonStyle> {
    val uuid = style.uuid
    var name by mutableStateOf(style.name)
    var animateSwap by mutableStateOf(style.animateSwap)
    var commonStyle by mutableStateOf(style.commonStyle)
    var lightStyle = ObservableStyleConfig(style.lightStyle)
    var darkStyle = ObservableStyleConfig(style.darkStyle)

    override fun pack(): ButtonStyle {
        return ButtonStyle(
            name = this.name,
            uuid = this.uuid,
            animateSwap = this.animateSwap,
            commonStyle = this.commonStyle,
            lightStyle = this.lightStyle.pack(),
            darkStyle = this.darkStyle.pack()
        )
    }

    override fun isModified(): Boolean {
        return style.isModified(pack())
    }
}

val DefaultObservableButtonStyle = ObservableButtonStyle(DefaultButtonStyle)

fun ObservableButtonStyle.cloneNew(): ObservableButtonStyle {
    return ObservableButtonStyle(pack().cloneNew())
}
