package com.movtery.layer_controller.layout

import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.JoystickStyle
import com.movtery.layer_controller.data.lang.EmptyTranslatableString
import com.movtery.layer_controller.data.lang.TranslatableString
import com.movtery.layer_controller.data.legacy.LegacySpecial
import com.movtery.layer_controller.layout.ControlLayout.Info
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.observable.isModified
import com.movtery.layer_controller.updateLayoutToNew
import com.movtery.layer_controller.utils.layoutJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class ControlLayout(
    @SerialName("info")
    val info: Info,
    @SerialName("layers")
    val layers: List<ControlLayer> = emptyList(),
    @SerialName("styles")
    val styles: List<ButtonStyle> = emptyList(),
    @SerialName("joystickStyles")
    val joystickStyles: List<JoystickStyle> = emptyList(),
    @SerialName("editorVersion")
    val editorVersion: Int
): Modifiable<ControlLayout> {
    @Serializable
    data class Info(
        @SerialName("name")
        val name: TranslatableString,
        @SerialName("author")
        val author: TranslatableString,
        @SerialName("description")
        val description: TranslatableString,
        @SerialName("versionCode")
        val versionCode: Int,
        @SerialName("versionName")
        val versionName: String
    ): Modifiable<Info> {
        override fun isModified(other: Info): Boolean {
            return other.name.isModified(this.name) ||
                    other.author.isModified(this.author) ||
                    other.description.isModified(this.description) ||
                    this.versionCode != other.versionCode ||
                    this.versionName != other.versionName
        }
    }

    override fun isModified(other: ControlLayout): Boolean {
        return this.info.isModified(other.info) ||
                this.layers.isModified(other.layers) ||
                this.styles.isModified(other.styles) ||
                this.joystickStyles.isModified(other.joystickStyles) ||
                this.editorVersion != other.editorVersion
    }
}

val EmptyLayoutInfo = Info(
    name = EmptyTranslatableString,
    author = EmptyTranslatableString,
    description = EmptyTranslatableString,
    versionCode = 0,
    versionName = ""
)

val EmptyControlLayout = ControlLayout(
    editorVersion = EDITOR_VERSION,
    info = EmptyLayoutInfo,
    layers = emptyList(),
    styles = emptyList(),
    joystickStyles = emptyList(),
)

fun loadLayoutFromFile(file: File): ControlLayout {
    val jsonString = file.readText()
    return loadLayoutFromString(jsonString)
}

fun loadLayoutFromString(jsonString: String): ControlLayout {
    val jsonObject = layoutJson.decodeFromString<JsonObject>(jsonString)
    if (jsonObject["editorVersion"] == null) throw SerializationException("The file does not contain the key \"editorVersion\".")
    val version = jsonObject["editorVersion"]!!.jsonPrimitive.int
    if (version <= EDITOR_VERSION) {
        val legacyJoystickStyle = if (version < 12) {
            jsonObject["special"]?.let { specialElement ->

                try {
                    layoutJson.decodeFromJsonElement(LegacySpecial.serializer(), specialElement).joystickStyle?.toJoystickStyle()
                } catch (_: Exception) {
                    null
                }
            }
        } else null

        var layout = layoutJson.decodeFromString<ControlLayout>(jsonString)
        if (version < EDITOR_VERSION) layout = updateLayoutToNew(layout)

        if (legacyJoystickStyle != null) {

            layout = layout.copy(
                joystickStyles = layout.joystickStyles + legacyJoystickStyle
            )
        }

        return layout
    } else {
        throw IllegalArgumentException("Control layout versions are not supported!")
    }
}

fun loadLayoutFromFileUncheck(file: File): ControlLayout {
    val jsonString = file.readText()
    return layoutJson.decodeFromString<ControlLayout>(jsonString)
}
