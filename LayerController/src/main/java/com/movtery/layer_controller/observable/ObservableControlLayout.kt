package com.movtery.layer_controller.observable

import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.JoystickStyle
import com.movtery.layer_controller.layout.ControlLayer
import com.movtery.layer_controller.layout.ControlLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ObservableControlLayout(
    private val layout: ControlLayout
): Packable<ControlLayout> {
    val info = ObservableControlInfo(layout.info)

    private val _layers = MutableStateFlow(layout.layers.map { ObservableControlLayer(it) })
    val layers = _layers.asStateFlow()

    private val _styles = MutableStateFlow(layout.styles.map { ObservableButtonStyle(it) })
    val styles = _styles.asStateFlow()

    private val _joystickStyles = MutableStateFlow(layout.joystickStyles.map { ObservableJoystickStyle(it) })
    val joystickStyles = _joystickStyles.asStateFlow()

    fun addLayer(layer: ControlLayer): ObservableControlLayer {
        val newLayer = ObservableControlLayer(layer)

        _layers.update { listOf(newLayer) + it }
        return newLayer
    }

    fun removeLayer(uuid: String) {
        _layers.update { oldLayers ->
            oldLayers.filterNot { it.uuid == uuid }
        }
    }

    fun mergeDownward(layer: ObservableControlLayer) {
        _layers.update { oldLayers ->
            if (oldLayers.isEmpty()) return@update oldLayers

            val layers = oldLayers.toMutableList()
            val index = layers.indexOf(layer)
            if (index == -1 || index + 1 >= layers.size) return@update oldLayers

            val downLayer = layers[index + 1]

            layer.normalButtons.value.takeIf {
                it.isNotEmpty()
            }?.let {
                downLayer.addAllNormalButton(it)
            }

            layer.textBoxes.value.takeIf {
                it.isNotEmpty()
            }?.let {
                downLayer.addAllTextBox(it)
            }

            layer.joystickButtons.value.takeIf {
                it.isNotEmpty()
            }?.let {
                downLayer.addAllJoystickButton(it)
            }

            layers.removeAt(index)
            layers
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        _layers.update { oldLayers ->
            oldLayers.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    fun addStyle(style: ButtonStyle) {
        _styles.update { it + ObservableButtonStyle(style) }
    }

    fun replaceStyle(style: ButtonStyle) {
        _styles.update { oldStyles ->
            oldStyles.map { if (it.uuid == style.uuid) ObservableButtonStyle(style) else it }
        }
    }

    fun cloneStyle(style: ObservableButtonStyle) {
        _styles.update { it + style.cloneNew() }
    }

    fun removeStyle(uuid: String) {
        _styles.update { oldStyles ->
            oldStyles.filterNot { it.uuid == uuid }
        }
        layers.value.forEach { layer ->
            layer.normalButtons.value.forEach { button ->
                if (button.buttonStyle == uuid) {
                    button.buttonStyle = null
                }
            }
            layer.textBoxes.value.forEach { textBox ->
                if (textBox.buttonStyle == uuid) {
                    textBox.buttonStyle = null
                }
            }
        }
    }

    fun addJoystickStyle(style: JoystickStyle) {
        _joystickStyles.update { it + ObservableJoystickStyle(style) }
    }

    fun replaceJoystickStyle(style: JoystickStyle) {
        _joystickStyles.update { oldStyles ->
            oldStyles.map { if (it.uuid == style.uuid) ObservableJoystickStyle(style) else it }
        }
    }

    fun cloneJoystickStyle(style: ObservableJoystickStyle) {
        _joystickStyles.update { it + style.cloneNew() }
    }

    fun removeJoystickStyle(uuid: String) {
        _joystickStyles.update { oldStyles ->
            oldStyles.filterNot { it.uuid == uuid }
        }
        layers.value.forEach { layer ->
            layer.joystickButtons.value.forEach { joystick ->
                if (joystick.joystickStyleId == uuid) {
                    joystick.joystickStyleId = null
                }
            }
        }
    }

    fun applyEditorHide() {
        layers.value.forEach { layer ->
            layer.hide = layer.editorHide
        }
    }

    override fun pack(): ControlLayout {
        return ControlLayout(
            info = info.pack(),
            layers = _layers.value.map { it.pack() },
            styles = _styles.value.map { it.pack() },
            joystickStyles = _joystickStyles.value.map { it.pack() },
            editorVersion = EDITOR_VERSION
        )
    }

    override fun isModified(): Boolean {
        return this.layout.isModified(pack())
    }
}
