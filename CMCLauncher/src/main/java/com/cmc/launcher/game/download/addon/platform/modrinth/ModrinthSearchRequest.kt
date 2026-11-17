package com.cmc.launcher.game.download.addon.platform.modrinth

import com.cmc.launcher.game.download.addon.platform.PlatformSortField
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthFacet
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ProjectTypeFacet
import com.cmc.launcher.game.download.addon.platform.modrinth.models.toFacetsString
import io.ktor.http.Parameters

data class ModrinthSearchRequest(

    val query: String = "",

    val facets: List<ModrinthFacet> = listOf(ProjectTypeFacet.MOD),

    val index: PlatformSortField = PlatformSortField.RELEVANCE,

    val offset: Int = 0,

    val limit: Int = 20
) {

    fun toParameters(): Parameters = Parameters.build {
        append("query", query)
        append("facets", facets.toFacetsString())
        append("limit", limit.toString())
        append("index", index.modrinth)
        append("offset", offset.toString())
    }
}
