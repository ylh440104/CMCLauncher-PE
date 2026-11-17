package com.movtery.layer_controller.data.lang

import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.compareLangTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class LocalizedString(
    @SerialName("language_tag")
    val languageTag: String,
    @SerialName("value")
    val value: String
): Modifiable<LocalizedString> {
    override fun isModified(other: LocalizedString): Boolean {
        return this.languageTag != other.languageTag ||
                this.value != other.value
    }
}

val EmptyLocalizedString = LocalizedString(languageTag = "", value = "")

fun LocalizedString.check(
    locale: Locale = Locale.getDefault()
): String? = value.takeIf {
    locale.compareLangTag(languageTag)
}
