package com.cmc.launcher.game.version.multiplayer.description

import com.cmc.launcher.game.text.TextColor
import kotlinx.serialization.Serializable

private fun flattenComponents(
    description: ComponentDescription,
    out: StringBuilder
) {
    if (description.text.isNotEmpty()) {
        out.append(description.text)
    }

    description.extra.forEach { child ->
        flattenComponents(child, out)
    }
}

@Serializable
data class ComponentDescriptionRoot(
    val values: List<ComponentDescription>
): ServerDescription {
    override fun toString(): String {
        val content = buildString {
            values.forEach {
                flattenComponents(it, this@buildString)
            }
        }
        return "ComponentDescriptionRoot:\n$content"
    }
}

@Serializable
data class ComponentDescription(
    val text: String,
    val color: TextColor? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underlined: Boolean? = null,
    val strikethrough: Boolean? = null,
    val obfuscated: Boolean? = null,
    val extra: List<ComponentDescription> = emptyList()
): ServerDescription {
    override fun toString(): String {
        val content = buildString {
            flattenComponents(this@ComponentDescription, this@buildString)
        }
        return "ComponentDescription:\n$content"
    }
}
