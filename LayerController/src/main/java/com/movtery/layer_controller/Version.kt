package com.movtery.layer_controller

import com.movtery.layer_controller.data.TextAlignment
import com.movtery.layer_controller.layout.ControlLayout

internal const val EDITOR_VERSION = 12

internal fun updateLayoutToNew(
    layout: ControlLayout
): ControlLayout {
    return when (layout.editorVersion) {
        1 -> updateLayoutToNew(update1To2(layout))
        2 -> updateLayoutToNew(update2To3(layout))
        3 -> updateLayoutToNew(update3To4(layout))
        4, 5, 6, 7, 8, 9 -> updateLayoutToNew(update4To10(layout))
        10 -> updateLayoutToNew(update10To11(layout))
        11 -> updateLayoutToNew(update11To12(layout))
        else -> layout
    }
}

private fun update1To2(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 2,
    layers = layout.layers.map { layer ->
        layer.copy(
            normalButtons = layer.normalButtons.map { data ->
                data.copy(
                    position = data.position.copy(
                        x = data.position.x * 10,
                        y = data.position.y * 10
                    ),
                    buttonSize = data.buttonSize.copy(
                        widthPercentage = data.buttonSize.widthPercentage * 10,
                        heightPercentage = data.buttonSize.heightPercentage * 10
                    )
                )
            },
            textBoxes = layer.textBoxes.map { data ->
                data.copy(
                    position = data.position.copy(
                        x = data.position.x * 10,
                        y = data.position.y * 10
                    ),
                    buttonSize = data.buttonSize.copy(
                        widthPercentage = data.buttonSize.widthPercentage * 10,
                        heightPercentage = data.buttonSize.heightPercentage * 10
                    )
                )
            }
        )
    }
)

private fun update2To3(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 3,
    layers = layout.layers.map { layer ->
        layer.copy(
            hideWhenMouse = true,
            hideWhenGamepad = true
        )
    }
)

private fun update3To4(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 4,
    layers = layout.layers.map { layer ->
        layer.copy(
            normalButtons = layer.normalButtons.map { data ->
                data.copy(
                    textAlignment = TextAlignment.Left,
                    textBold = false,
                    textItalic = false,
                    textUnderline = false
                )
            },
            textBoxes = layer.textBoxes.map { data ->
                data.copy(
                    textAlignment = TextAlignment.Left,
                    textBold = false,
                    textItalic = false,
                    textUnderline = false
                )
            }
        )
    }
)

private fun update4To10(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 10
)

private fun update10To11(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 11,
    styles = layout.styles.map { style ->
        style.copy(

            commonStyle = false
        )
    }
)

private fun update11To12(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 12
)
