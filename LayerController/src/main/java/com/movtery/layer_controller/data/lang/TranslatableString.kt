package com.movtery.layer_controller.data.lang

import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.observable.isModified
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class TranslatableString(
    @SerialName("default")
    val default: String,
    @SerialName("matchQueue")
    val matchQueue: List<LocalizedString>
): Modifiable<TranslatableString> {
    fun translate(locale: Locale = Locale.getDefault()): String {
        matchQueue.forEach { ls ->
            val value = ls.check(locale)
            if (value != null) return value
        }
        return default
    }

    override fun isModified(other: TranslatableString): Boolean {
        return this.default != other.default ||
                this.matchQueue.isModified(other.matchQueue)
    }
}

val EmptyTranslatableString = TranslatableString("", emptyList())

fun createTranslatable(default: String, vararg matchQueue: LocalizedString): TranslatableString {
    return TranslatableString(default = default, matchQueue = matchQueue.toList())
}
