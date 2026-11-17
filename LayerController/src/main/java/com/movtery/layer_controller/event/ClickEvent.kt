package com.movtery.layer_controller.event

import com.movtery.layer_controller.observable.Modifiable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClickEvent(
    @SerialName("type")
    val type: Type,
    @SerialName("key")
    val key: String
): Modifiable<ClickEvent> {
    @Serializable
    enum class Type {

        @SerialName("key")
        Key,

        @SerialName("launcher_event")
        LauncherEvent,

        @SerialName("switch_layer")
        SwitchLayer,

        @SerialName("show_layer")
        ShowLayer,

        @SerialName("hide_layer")
        HideLayer,

        @SerialName("send_text")
        SendText;

        fun isAboutLayers(): Boolean =
            this == SwitchLayer ||
                    this == ShowLayer ||
                    this == HideLayer
    }

    fun isAboutLayers(): Boolean = type.isAboutLayers()

    override fun isModified(other: ClickEvent): Boolean {
        return this.type != other.type ||
                this.key != other.key
    }
}
