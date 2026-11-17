package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.JoystickData
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.TextData
import com.movtery.layer_controller.layout.ControlLayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ObservableControlLayer(
    private val layer: ControlLayer
): Packable<ControlLayer> {
    val uuid: String = layer.uuid

    var editorHide by mutableStateOf(layer.hide)

    var name by mutableStateOf(layer.name)
    var hide by mutableStateOf(editorHide)
    var hideWhenMouse by mutableStateOf(layer.hideWhenMouse)
    var hideWhenGamepad by mutableStateOf(layer.hideWhenGamepad)
    var visibilityType by mutableStateOf(layer.visibilityType)

    private val _normalButtons = MutableStateFlow(layer.normalButtons.map { ObservableNormalData(it) })
    val normalButtons = _normalButtons.asStateFlow()

    private val _textBoxes = MutableStateFlow(layer.textBoxes.map { ObservableTextData(it) })
    val textBoxes = _textBoxes.asStateFlow()

    private val _joystickButtons = MutableStateFlow(layer.joystickButtons.map { ObservableJoystickData(it) })
    val joystickButtons = _joystickButtons.asStateFlow()

    fun addNormalButton(button: NormalData) {
        addNormalButton(ObservableNormalData(button))
    }

    fun addNormalButton(button: ObservableNormalData) {
        _normalButtons.update { it + button }
    }

    fun addAllNormalButton(buttons: List<ObservableNormalData>) {
        _normalButtons.update { it + buttons }
    }

    fun removeNormalButton(uuid: String) {
        _normalButtons.update { oldList ->
            oldList.filterNot { it.uuid == uuid }
        }
    }

    fun addTextBox(textBox: TextData) {
        addTextBox(ObservableTextData(textBox))
    }

    fun addTextBox(textBox: ObservableTextData) {
        _textBoxes.update { it + textBox }
    }

    fun addAllTextBox(textBoxes: List<ObservableTextData>) {
        _textBoxes.update { it + textBoxes }
    }

    fun removeTextBox(uuid: String) {
        _textBoxes.update { oldList ->
            oldList.filterNot { it.uuid == uuid }
        }
    }

    fun addJoystickButton(joystick: JoystickData) {
        addJoystickButton(ObservableJoystickData(joystick))
    }

    fun addJoystickButton(joystick: ObservableJoystickData) {
        _joystickButtons.update { it + joystick }
    }

    fun addAllJoystickButton(joysticks: List<ObservableJoystickData>) {
        _joystickButtons.update { it + joysticks }
    }

    fun removeJoystickButton(uuid: String) {
        _joystickButtons.update { oldList ->
            oldList.filterNot { it.uuid == uuid }
        }
    }

    override fun pack(): ControlLayer {
        return ControlLayer(
            name = name,
            uuid = uuid,
            hide = editorHide,
            hideWhenMouse = hideWhenMouse,
            hideWhenGamepad = hideWhenGamepad,
            visibilityType = visibilityType,
            normalButtons = _normalButtons.value.map { it.packNormal() },
            textBoxes = _textBoxes.value.map { it.packText() },
            joystickButtons = _joystickButtons.value.map { it.packJoystick() }
        )
    }

    override fun isModified(): Boolean {
        return this.layer.isModified(pack())
    }
}
