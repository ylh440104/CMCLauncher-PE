package com.cmc.launcher.game.download.addon.platform.modrinth

import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformFilterCode
import com.cmc.launcher.game.download.addon.platform.PlatformSearchData
import com.cmc.launcher.game.download.addon.platform.PlatformSearchResult
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthModLoaderCategory
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthSide
import com.cmc.launcher.game.download.addon.platform.modrinth.models.MonetizationStatus
import com.cmc.launcher.game.download.addon.platform.modrinth.models.mapModrinthCategory
import com.cmc.launcher.game.download.addon.platform.searchRankWithChineseBias
import com.cmc.launcher.game.download.addon.utils.getTranslations
import com.cmc.launcher.ui.screens.content.download.addon.elements.AddonPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ModrinthSearchResult(

    @SerialName("hits")
    val hits: Array<ModrinthProject>,

    @SerialName("offset")
    val offset: Int,

    @SerialName("limit")
    val limit: Int,

    @SerialName("total_hits")
    val totalHits: Int
): PlatformSearchResult {
    @Serializable
    class ModrinthProject(

        @SerialName("project_id")
        val projectId: String,

        @SerialName("project_type")
        val projectType: String,

        @SerialName("slug")
        val slug: String? = null,

        @SerialName("author")
        val author: String,

        @SerialName("title")
        val title: String? = null,

        @SerialName("description")
        val description: String? = null,

        @SerialName("categories")
        val categories: Array<String>? = null,

        @SerialName("display_categories")
        val displayCategories: Array<String>? = null,

        @SerialName("versions")
        val versions: Array<String>,

        @SerialName("downloads")
        val downloads: Long,

        @SerialName("follows")
        val follows: Long,

        @SerialName("icon_url")
        val iconUrl: String? = null,

        @SerialName("date_created")
        val dateCreated: String,

        @SerialName("date_modified")
        val dateModified: String,

        @SerialName("latest_version")
        val latestVersion: String? = null,

        @SerialName("license")
        val license: String,

        @SerialName("client_side")
        val clientSide: ModrinthSide? = null,

        @SerialName("server_side")
        val serverSide: ModrinthSide? = null,

        @SerialName("gallery")
        val gallery: Array<String>? = null,

        @SerialName("featured_gallery")
        val featuredGallery: String? = null,

        @SerialName("color")
        val color: Int? = null,

        @SerialName("thread_id")
        val threadId: String? = null,

        @SerialName("monetization_status")
        val monetizationStatus: MonetizationStatus? = null,
    ) : PlatformSearchData {
        override fun platform(): Platform = Platform.MODRINTH

        override fun platformId(): String = projectId

        override fun platformTitle(): String = title ?: ""

        override fun platformDescription(): String = description ?: ""

        override fun platformAuthors(): List<String>? = author.takeIf { it.isNotEmpty() }?.let { listOf(it) }

        override fun platformIconUrl(): String? = iconUrl

        override fun platformDownloadCount(): Long = downloads

        override fun platformFollows(): Long = follows

        override fun platformModLoaders(): List<PlatformDisplayLabel>? {
            val modloaders = displayCategories
                ?.mapNotNull { string ->
                    ModrinthModLoaderCategory.entries.find { it.facetValue() == string }
                }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }

            return modloaders?.sortedWith { o1, o2 -> o1.index() - o2.index() }
        }

        override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
            val categories = displayCategories
                ?.mapNotNull { string ->
                    string.mapModrinthCategory(classes)
                }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: categories
                    ?.take(4)
                    ?.mapNotNull { string ->
                        string.mapModrinthCategory(classes)
                    }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }

            return categories?.sortedWith { o1, o2 -> o1.index() - o2.index() }
        }
    }

    override fun getAddonPage(classes: PlatformClasses): AddonPage {
        val mcmodData = hits.map {
            it to classes.getTranslations()?.getInfoByCurseForgeId(it.slug)
        }

        return AddonPage(
            pageNumber = this.offset / this.limit + 1,
            pageIndex = this.offset,
            totalPage = (this.totalHits + this.limit - 1) / this.limit,
            isLastPage = (this.offset + this.limit) >= this.totalHits,
            data = mcmodData
        )
    }

    override fun processChineseSearchResults(
        searchFilter: String,
        classes: PlatformClasses
    ): PlatformSearchResult {
        val newHits = hits.toList()
            .searchRankWithChineseBias(searchFilter, classes) { it.slug }
            .toTypedArray()
        return ModrinthSearchResult(newHits, offset, limit, totalHits)
    }
}
