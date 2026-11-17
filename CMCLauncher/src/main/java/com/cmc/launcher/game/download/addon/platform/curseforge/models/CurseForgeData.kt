package com.cmc.launcher.game.download.addon.platform.curseforge.models

import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformFilterCode
import com.cmc.launcher.game.download.addon.platform.PlatformSearchData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgeData(

    @SerialName("id")
    val id: Int,

    @SerialName("gameId")
    val gameId: Int,

    @SerialName("name")
    val name: String,

    @SerialName("slug")
    val slug: String,

    @SerialName("links")
    val links: Links,

    @SerialName("summary")
    val summary: String,

    @SerialName("status")
    val status: Int,

    @SerialName("downloadCount")
    val downloadCount: Long,

    @SerialName("isFeatured")
    val isFeatured: Boolean,

    @SerialName("primaryCategoryId")
    val primaryCategoryId: Int,

    @SerialName("categories")
    val categories: Array<Category>,

    @SerialName("classId")
    val classId: Int? = null,

    @SerialName("authors")
    val authors: Array<Author>,

    @SerialName("logo")
    val logo: Asset? = null,

    @SerialName("screenshots")
    val screenshots: Array<Asset> = emptyArray(),

    @SerialName("mainFileId")
    val mainFileId: Int,

    @SerialName("latestFiles")
    val latestFiles: Array<CurseForgeFile>,

    @SerialName("latestFilesIndexes")
    val latestFilesIndexes: Array<CurseForgeFileIndex>,

    @SerialName("latestEarlyAccessFilesIndexes")
    val latestEarlyAccessFilesIndexes: Array<CurseForgeFileIndex>? = null,

    @SerialName("dateCreated")
    val dateCreated: String,

    @SerialName("dateModified")
    val dateModified: String,

    @SerialName("dateReleased")
    val dateReleased: String,

    @SerialName("allowModDistribution")
    val allowModDistribution: Boolean? = null,

    @SerialName("gamePopularityRank")
    val gamePopularityRank: Int,

    @SerialName("isAvailable")
    val isAvailable: Boolean,

    @SerialName("thumbsUpCount")
    val thumbsUpCount: Int,

    @SerialName("rating")
    val rating: Double? = null
) : PlatformSearchData {
    @Serializable
    class Links(

        @SerialName("websiteUrl")
        val websiteUrl: String? = null,

        @SerialName("wikiUrl")
        val wikiUrl: String? = null,

        @SerialName("issuesUrl")
        val issuesUrl: String? = null,

        @SerialName("sourceUrl")
        val sourceUrl: String? = null
    )

    @Serializable
    class Category(

        @SerialName("id")
        val id: Int,

        @SerialName("gameId")
        val gameId: Int,

        @SerialName("name")
        val name: String,

        @SerialName("slug")
        val slug: String,

        @SerialName("url")
        val url: String,

        @SerialName("iconUrl")
        val iconUrl: String,

        @SerialName("dateModified")
        val dateModified: String,

        @SerialName("isClass")
        val isClass: Boolean,

        @SerialName("classId")
        val classId: Int? = null,

        @SerialName("parentCategoryId")
        val parentCategoryId: Int? = null,

        @SerialName("displayIndex")
        val displayIndex: Int? = null
    )

    @Serializable
    class Author(
        @SerialName("id")
        val id: Int,

        @SerialName("name")
        val name: String,

        @SerialName("url")
        val url: String,

        @SerialName("avatarUrl")
        val avatarUrl: String? = null
    )

    @Serializable
    class Asset(
        @SerialName("id")
        val id: Int,

        @SerialName("modId")
        val modId: Int,

        @SerialName("title")
        val title: String,

        @SerialName("description")
        val description: String,

        @SerialName("thumbnailUrl")
        val thumbnailUrl: String,

        @SerialName("url")
        val url: String
    )

    override fun platform(): Platform = Platform.CURSEFORGE

    override fun platformId(): String = id.toString()

    override fun platformTitle(): String = name

    override fun platformDescription(): String = summary

    override fun platformAuthors(): List<String>? = authors.mapNotNull {
        it.name.takeIf { it.isNotEmpty() }
    }.takeIf { it.isNotEmpty() }

    override fun platformIconUrl(): String? = logo?.url

    override fun platformDownloadCount(): Long = downloadCount

    override fun platformFollows(): Long? = null

    override fun platformModLoaders(): List<PlatformDisplayLabel>? {
        return latestFilesIndexes.mapNotNull {
            it.modLoader
        }.toSet()
            .takeIf { it.isNotEmpty() }
            ?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }

    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
        return categories.mapNotNull {
            it.id.toString().mapCurseForgeCategory(classes)
        }.toSet().takeIf { it.isNotEmpty() }
            ?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }
}

fun CurseForgeData.isApproved(): Boolean = this.status == 4
