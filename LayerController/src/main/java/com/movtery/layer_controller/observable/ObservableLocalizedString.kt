package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.lang.LocalizedString
import com.movtery.layer_controller.utils.compareLangTag
import java.util.Locale

class ObservableLocalizedString(
    private val string: LocalizedString
): Packable<LocalizedString?> {
    var languageTag by mutableStateOf(string.languageTag)
    var value by mutableStateOf(string.value)

    override fun pack(): LocalizedString? {
        if (languageTag.isEmpty() || languageTag.isBlank()) return null
        if (value.isEmpty() || value.isBlank()) return null
        return LocalizedString(
            languageTag = languageTag,
            value = value
        )
    }

    override fun isModified(): Boolean {
        val packedNew = pack() ?: return true
        return this.string.isModified(packedNew)
    }
}

fun ObservableLocalizedString.check(
    locale: Locale = Locale.getDefault()
): String? = value.takeIf {
    locale.compareLangTag(languageTag)
}
