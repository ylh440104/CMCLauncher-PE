package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.ButtonStyle

class ObservableStyleConfig(
    private val config: ButtonStyle.StyleConfig
): Packable<ButtonStyle.StyleConfig> {
    var alpha by mutableFloatStateOf(config.alpha)
    var backgroundColor by mutableStateOf(config.backgroundColor)
    var contentColor by mutableStateOf(config.contentColor)
    var fontSize by mutableStateOf(config.fontSize)
    var borderWidth by mutableIntStateOf(config.borderWidth)
    var borderColor by mutableStateOf(config.borderColor)
    var borderRadius by mutableStateOf(config.borderRadius)
    var pressedAlpha by mutableFloatStateOf(config.pressedAlpha)
    var pressedBackgroundColor by mutableStateOf(config.pressedBackgroundColor)
    var pressedContentColor by mutableStateOf(config.pressedContentColor)
    var pressedFontSize by mutableStateOf(config.pressedFontSize)
    var pressedBorderWidth by mutableIntStateOf(config.pressedBorderWidth)
    var pressedBorderColor by mutableStateOf(config.pressedBorderColor)
    var pressedBorderRadius by mutableStateOf(config.pressedBorderRadius)

    override fun pack(): ButtonStyle.StyleConfig {
        return ButtonStyle.StyleConfig(
            alpha = this.alpha,
            backgroundColor = this.backgroundColor,
            contentColor = this.contentColor,
            fontSize = this.fontSize,
            borderWidth = this.borderWidth,
            borderColor = this.borderColor,
            borderRadius = this.borderRadius,
            pressedAlpha = this.pressedAlpha,
            pressedBackgroundColor = this.pressedBackgroundColor,
            pressedContentColor = this.pressedContentColor,
            pressedFontSize = this.pressedFontSize,
            pressedBorderWidth = this.pressedBorderWidth,
            pressedBorderColor = this.pressedBorderColor,
            pressedBorderRadius = this.pressedBorderRadius
        )
    }

    override fun isModified(): Boolean {
        return this.alpha != config.alpha ||
                this.backgroundColor != config.backgroundColor ||
                this.contentColor != config.contentColor ||
                this.fontSize != config.fontSize ||
                this.borderWidth != config.borderWidth ||
                this.borderColor != config.borderColor ||
                config.borderRadius.isModified(this.borderRadius) ||
                this.pressedAlpha != config.pressedAlpha ||
                this.pressedBackgroundColor != config.pressedBackgroundColor ||
                this.pressedContentColor != config.pressedContentColor ||
                this.pressedFontSize != config.pressedFontSize ||
                this.pressedBorderWidth != config.pressedBorderWidth ||
                this.pressedBorderColor != config.pressedBorderColor ||
                config.pressedBorderRadius.isModified(this.pressedBorderRadius)
    }
}
