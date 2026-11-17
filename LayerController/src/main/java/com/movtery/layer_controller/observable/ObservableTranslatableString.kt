package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.lang.EmptyLocalizedString
import com.movtery.layer_controller.data.lang.LocalizedString
import com.movtery.layer_controller.data.lang.TranslatableString
import java.util.Locale

class ObservableTranslatableString(
    private val text: TranslatableString
): Packable<TranslatableString> {
    var default by mutableStateOf(text.default)
    var matchQueue = mutableStateListOf<ObservableLocalizedString>()
        .apply { addAll(getMatchQueues()) }
        private set

    private fun getMatchQueues() = text.matchQueue.map { ObservableLocalizedString(it) }

    fun reset() {
        default = text.default
        matchQueue.clear()
        matchQueue.addAll(getMatchQueues())
    }

    fun translate(locale: Locale = Locale.getDefault()): String {
        matchQueue.sortedByDescending {
            it.languageTag.contains("-")
        }.forEach { ls ->
            val value = ls.check(locale)
            if (value != null) return value
        }
        return default
    }

    fun deleteLocalizedString(string: ObservableLocalizedString) {
        matchQueue.removeIf {
            string.languageTag == it.languageTag && string.value == it.value
        }
    }

    fun addLocalizedString(string: LocalizedString = EmptyLocalizedString) {
        if (matchQueue.any { it.languageTag == string.languageTag && it.value == string.value }) return
        matchQueue.add(ObservableLocalizedString(string))
    }

    override fun pack(): TranslatableString {
        return TranslatableString(
            default = default,
            matchQueue = matchQueue.mapNotNull { it.pack() }
        )
    }

    override fun isModified(): Boolean {
        return this.text.isModified(pack())
    }
}
