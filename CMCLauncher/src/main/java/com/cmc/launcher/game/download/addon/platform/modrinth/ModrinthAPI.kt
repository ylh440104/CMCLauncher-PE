package com.cmc.launcher.game.download.addon.platform.modrinth

import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformSearchFilter
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthFacet
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthModLoaderCategory
import com.cmc.launcher.game.download.addon.platform.modrinth.models.VersionFacet
import com.cmc.launcher.utils.string.isNotEmptyOrBlank

const val MODRINTH_API = "https://api.modrinth.com/v2"

const val MCIM_MODRINTH_API = "https://mod.mcimirror.top/modrinth/v2"

fun PlatformSearchFilter.toModrinthRequest(
    query: String,
    platformClasses: PlatformClasses
): ModrinthSearchRequest {
    val modrinthVersion = gameVersion.takeIf { it.isNotEmptyOrBlank() }?.let { version ->
        VersionFacet(version.trim())
    }
    val modrinthCategories = categories.map { category ->
        category as? ModrinthFacet
    }.toTypedArray()
    val modrinthModLoader = modloader?.let { modloader ->
        modloader as? ModrinthModLoaderCategory
    }

    return ModrinthSearchRequest(
        query = query,
        facets = listOfNotNull(
            platformClasses.modrinth!!,
            modrinthVersion,
            *modrinthCategories,
            modrinthModLoader
        ),
        index = sortField,
        offset = index,
        limit = limit
    )
}
