package com.movtery.layer_controller.layout

import com.movtery.layer_controller.data.JoystickData
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.TextData
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.observable.isModified
import com.movtery.layer_controller.utils.randomUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ControlLayer(
    @SerialName("name")
    val name: String,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("hide")
    val hide: Boolean,
    @SerialName("hideWhenMouse")
    val hideWhenMouse: Boolean = true,
    @SerialName("hideWhenGamepad")
    val hideWhenGamepad: Boolean = true,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType,
    @SerialName("normalButtons")
    val normalButtons: List<NormalData> = emptyList(),
    @SerialName("textBoxes")
    val textBoxes: List<TextData> = emptyList(),
    @SerialName("joystickButtons")
    val joystickButtons: List<JoystickData> = emptyList()
): Modifiable<ControlLayer> {
    override fun isModified(other: ControlLayer): Boolean {
        return this.name != other.name ||
                this.uuid != other.uuid ||
                this.hide != other.hide ||
                this.hideWhenMouse != other.hideWhenMouse ||
                this.hideWhenGamepad != other.hideWhenGamepad ||
                this.visibilityType != other.visibilityType ||
                this.normalButtons.isModified(other.normalButtons) ||
                this.textBoxes.isModified(other.textBoxes) ||
                this.joystickButtons.isModified(other.joystickButtons)
    }
}

fun createNewLayer(defaultLayerName: String = ""): ControlLayer {
    return ControlLayer(
        name = defaultLayerName,
        uuid = randomUUID(),
        hide = false,
        hideWhenMouse = true,
        hideWhenGamepad = true,
        visibilityType = VisibilityType.ALWAYS
    )
}
