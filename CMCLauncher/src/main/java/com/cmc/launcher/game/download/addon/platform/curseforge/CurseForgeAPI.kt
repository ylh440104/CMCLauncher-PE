package com.cmc.launcher.game.download.addon.platform.curseforge

import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformSearchFilter
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeCategory
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeModLoader
import com.cmc.launcher.utils.string.isNotEmptyOrBlank

const val CURSEFORGE_API = "https://api.curseforge.com/v1"

const val MCIM_CURSEFORGE_API = "https://mod.mcimirror.top/curseforge/v1"

fun PlatformSearchFilter.toCurseForgeRequest(
    query: String,
    platformClasses: PlatformClasses
): CurseForgeSearchRequest {
    val curseforgeCategories = categories.map { category ->
        category as? CurseForgeCategory
    }.toTypedArray()

    return CurseForgeSearchRequest(
        classId = platformClasses.curseforge.classID,
        categories = setOfNotNull(
            *curseforgeCategories
        ),
        searchFilter = query,
        gameVersion = gameVersion.takeIf { it.isNotEmptyOrBlank() }?.trim(),
        sortField = sortField,
        modLoader = modloader as? CurseForgeModLoader,
        index = index,
        pageSize = limit
    )
}
