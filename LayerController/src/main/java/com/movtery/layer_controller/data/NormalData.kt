package com.movtery.layer_controller.data

import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.observable.isModified
import com.movtery.layer_controller.utils.getAButtonUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NormalData(
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
    val visibilityType: VisibilityType,
    @SerialName("clickEvents")
    private var _clickEvents: List<ClickEvent> = emptyList(),
    @SerialName("isSwipple")
    val isSwipple: Boolean,
    @SerialName("isPenetrable")
    val isPenetrable: Boolean,
    @SerialName("isToggleable")
    val isToggleable: Boolean
): Widget, Modifiable<NormalData> {
    val clickEvents: List<ClickEvent> get() = _clickEvents

    init {
        _clickEvents = _clickEvents.filterValidEvent()
    }

    override fun isModified(other: NormalData): Boolean {
        return this.text.isModified(other.text) ||
                this.uuid != other.uuid ||
                this.position.isModified(other.position) ||
                this.buttonSize.isModified(other.buttonSize) ||
                this.buttonStyle != other.buttonStyle ||
                this.textAlignment != other.textAlignment ||
                this.textBold != other.textBold ||
                this.textItalic != other.textItalic ||
                this.textUnderline != other.textUnderline ||
                this.visibilityType != other.visibilityType ||
                this._clickEvents.isModified(other._clickEvents) ||
                this.isSwipple != other.isSwipple ||
                this.isPenetrable != other.isPenetrable ||
                this.isToggleable != other.isToggleable
    }
}

internal fun List<ClickEvent>.filterValidEvent(): List<ClickEvent> {
    var foundValidSendText = false
    return filter { event ->
        if (event.type == ClickEvent.Type.SendText) {
            if (!foundValidSendText && event.key.isNotEmpty()) {
                foundValidSendText = true
                true
            } else {
                false
            }
        } else {
            true
        }
    }
}

fun NormalData.cloneNew(): NormalData = NormalData(
    text = this.text,
    uuid = getAButtonUUID(),
    position = CenterPosition,
    buttonSize = buttonSize,
    buttonStyle = buttonStyle,
    textAlignment = textAlignment,
    textBold = textBold,
    textItalic = textItalic,
    textUnderline = textUnderline,
    visibilityType = visibilityType,
    _clickEvents = clickEvents,
    isSwipple = isSwipple,
    isPenetrable = isPenetrable,
    isToggleable = isToggleable
)
