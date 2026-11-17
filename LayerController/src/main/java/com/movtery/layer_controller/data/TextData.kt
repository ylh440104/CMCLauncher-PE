package com.movtery.layer_controller.data

import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.getAButtonUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextData(
    @SerialName("text")
    val text: TranslatableString,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("position")
    val position: ButtonPosition,
    @SerialName("buttonSize")
    val buttonSize: ButtonSize,
    @SerialName("buttonStyle")
    val buttonStyle: String? = null,
    @SerialName("textAlignment")
    val textAlignment: TextAlignment = TextAlignment.Left,
    @SerialName("textBold")
    val textBold: Boolean = false,
    @SerialName("textItalic")
    val textItalic: Boolean = false,
    @SerialName("textUnderline")
    val textUnderline: Boolean = false,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType
): Widget, Modifiable<TextData> {
    override fun isModified(other: TextData): Boolean {
        return this.text.isModified(other.text) ||
                this.uuid != other.uuid ||
                this.position.isModified(other.position) ||
                this.buttonSize.isModified(other.buttonSize) ||
                this.buttonStyle != other.buttonStyle ||
                this.textAlignment != other.textAlignment ||
                this.textBold != other.textBold ||
                this.textItalic != other.textItalic ||
                this.textUnderline != other.textUnderline ||
                this.visibilityType != other.visibilityType
    }
}

fun TextData.cloneNew(): TextData = TextData(
    text = this.text,
    uuid = getAButtonUUID(),
    position = CenterPosition,
    buttonSize = buttonSize,
    buttonStyle = buttonStyle,
    textAlignment = textAlignment,
    textBold = textBold,
    textItalic = textItalic,
    textUnderline = textUnderline,
    visibilityType = visibilityType
)
