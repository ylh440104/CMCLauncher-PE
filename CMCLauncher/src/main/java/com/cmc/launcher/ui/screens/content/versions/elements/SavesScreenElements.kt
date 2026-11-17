package com.cmc.launcher.ui.screens.content.versions.elements

import com.cmc.launcher.game.version.saves.SaveData
import com.cmc.launcher.game.version.saves.isCompatible
import com.cmc.launcher.utils.string.stripColorCodes

sealed interface SavesOperation {
    data object None : SavesOperation

    data object Progress : SavesOperation

    data class QuickPlay(val saveData: SaveData) : SavesOperation

    data class RenameSave(val saveData: SaveData) : SavesOperation

    data class BackupSave(val saveData: SaveData) : SavesOperation

    data class DeleteSave(val saveData: SaveData) : SavesOperation
}

sealed interface SavesCompressOperation {
    data object None : SavesCompressOperation

    data class Compressing(val saveData: SaveData) : SavesCompressOperation

    data class Error(val throwable: Throwable) : SavesCompressOperation
}

data class SavesFilter(val onlyShowCompatible: Boolean, val saveName: String = "")

fun List<SaveData>.filterSaves(
    minecraftVersion: String,
    savesFilter: SavesFilter
) = this.filter {
    val isCompatible = !savesFilter.onlyShowCompatible || it.isCompatible(minecraftVersion)

    val nameMatches = savesFilter.saveName.isEmpty() ||

            it.levelName?.stripColorCodes()?.contains(savesFilter.saveName, true) == true ||
            it.saveFile.name.stripColorCodes().contains(savesFilter.saveName, true)

    isCompatible && nameMatches
}
