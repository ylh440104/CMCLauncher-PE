package com.cmc.launcher.ui.screens.content.versions.elements

import com.cmc.launcher.game.version.resource_pack.ResourcePackInfo

sealed interface ResourcePackOperation {
    data object None : ResourcePackOperation

    data object Progress : ResourcePackOperation

    data class RenamePack(val packInfo: ResourcePackInfo) : ResourcePackOperation

    data class DeletePack(val packInfo: ResourcePackInfo) : ResourcePackOperation
}

data class ResourcePackFilter(
    val onlyShowValid: Boolean,
    val filterName: String
)

fun List<ResourcePackInfo>.filterPacks(filter: ResourcePackFilter) = this.filter {
    val valid = !filter.onlyShowValid || it.isValid
    val nameMatched = filter.filterName.isEmpty() ||

            it.rawName.contains(filter.filterName, true)
    valid && nameMatched
}
