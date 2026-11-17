package com.cmc.launcher.game.version.mod.update

import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModManifest(
    val data: ModData,
    val new: PlatformVersion,
)

fun List<ModManifest>.allNews() = map { it.new }

fun List<ModManifest>.toSelectableList() = map { manifest ->
    SelectableModManifest(
        data = manifest.data,
        new = manifest.new,
    )
}

class SelectableModManifest(
    val data: ModData,
    val new: PlatformVersion,
) {
    private val _selected = MutableStateFlow(true)
    val selected = _selected.asStateFlow()

    fun updateSelected(value: Boolean) {
        _selected.update { value }
    }

    fun selected() = _selected.value
}

fun List<SelectableModManifest>.toFinalList() = mapNotNull { manifest ->
    if (manifest.selected()) {
        ModManifest(
            data = manifest.data,
            new = manifest.new,
        )
    } else null
}
