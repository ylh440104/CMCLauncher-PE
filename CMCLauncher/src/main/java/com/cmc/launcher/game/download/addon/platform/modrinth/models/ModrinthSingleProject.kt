package com.cmc.launcher.game.download.addon.platform.modrinth.models

import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformFilterCode
import com.cmc.launcher.game.download.addon.platform.PlatformProject
import com.cmc.launcher.game.download.addon.platform.UnsupportedClassesException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ModrinthSingleProject(

    @SerialName("slug")
    val slug: String,

    @SerialName("title")
    val title: String,

    @SerialName("description")
    val description: String,

    @SerialName("categories")
    val categories: Array<String>,

    @SerialName("client_side")
    val clientSide: ModrinthSide,

    @SerialName("server_side")
    val serverSide: ModrinthSide,

    @SerialName("body")
    val body: String,

    @SerialName("status")
    val status: String,

    @SerialName("requested_status")
    val requestedStatus: String? = null,

    @SerialName("additional_categories")
    val additionalCategories: Array<String>,

    @SerialName("issues_url")
    val issuesUrl: String? = null,

    @SerialName("source_url")
    val sourceUrl: String? = null,

    @SerialName("wiki_url")
    val wikiUrl: String? = null,

    @SerialName("discord_url")
    val discordUrl: String? = null,

    @SerialName("donation_urls")
    val donationUrls: Array<DonationUrl>,

    @SerialName("project_type")
    val projectType: String,

    @SerialName("downloads")
    val downloads: Long,

    @SerialName("icon_url")
    val iconUrl: String? = null,

    @SerialName("color")
    val color: Int? = null,

    @SerialName("thread_id")
    val threadId: String,

    @SerialName("monetization_status")
    val monetizationStatus: MonetizationStatus,

    @SerialName("id")
    val id: String,

    @SerialName("team")
    val team: String? = null,

    @SerialName("organization")
    val organization: String? = null,

    @SerialName("body_url")
    val bodyUrl: String? = null,

    @SerialName("moderator_message")
    val moderatorMessage: ModeratorMessage? = null,

    @SerialName("published")
    val published: String,

    @SerialName("updated")
    val updated: String,

    @SerialName("approved")
    val approved: String? = null,

    @SerialName("queued")
    val queued: String? = null,

    @SerialName("followers")
    val followers: Long,

    @SerialName("license")
    val license: License,

    @SerialName("versions")
    val versions: Array<String>,

    @SerialName("game_versions")
    val gameVersions: Array<String>,

    @SerialName("loaders")
    val loaders: Array<String>,

    @SerialName("gallery")
    val gallery: Array<Gallery>
): PlatformProject {
    @Serializable
    class DonationUrl(

        @SerialName("id")
        val id: String,

        @SerialName("platform")
        val platform: String,

        @SerialName("url")
        val url: String,
    )

    @Serializable
    class ModeratorMessage(

        @SerialName("message")
        val message: String,

        @SerialName("body")
        val body: String? = null
    )

    @Serializable
    class License(

        @SerialName("id")
        val id: String,

        @SerialName("name")
        val name: String,

        @SerialName("url")
        val url: String? = null
    )

    @Serializable
    class Gallery(

        @SerialName("url")
        val url: String,

        @SerialName("featured")
        val featured: Boolean,

        @SerialName("title")
        val title: String? = null,

        @SerialName("description")
        val description: String? = null,

        @SerialName("created")
        val created: String,

        @SerialName("ordering")
        val ordering: Int
    )

    override fun platform(): Platform = Platform.MODRINTH

    override fun platformId(): String = id

    override fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses {
        return projectType.mapModrinthType()?.platform ?: defaultClasses
    }

    override fun platformSlug(): String = slug

    override fun platformIconUrl(): String? = iconUrl

    override fun platformTitle(): String = title

    override fun platformSummary(): String = description

    override fun platformAuthors(): List<String>? = null

    override fun platformDownloadCount(): Long = downloads

    override fun platformFollows(): Long = followers

    override fun platformModLoaders(): List<PlatformDisplayLabel>? {
        val modloaders = loaders.mapNotNull { string ->
                ModrinthModLoaderCategory.entries.find { it.facetValue() == string }
            }.toSet().takeIf { it.isNotEmpty() }

        return modloaders?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }

    override fun checkClasses() {

        if (projectType.mapModrinthType() == null) throw UnsupportedClassesException(projectType)
    }

    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
        return categories.take(4)
            .mapNotNull { string ->
                string.mapModrinthCategory(classes)
            }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }

    override fun platformUrls(defaultClasses: PlatformClasses): PlatformProject.Urls {
        val classes = projectType.mapModrinthType()?.platform ?: defaultClasses
        return PlatformProject.Urls(
            projectUrl = "https://modrinth.com/${classes.modrinth!!.facetValue()}/${slug}",
            sourceUrl = sourceUrl,
            issuesUrl = issuesUrl,
            wikiUrl = wikiUrl
        )
    }

    override fun platformScreenshots(): List<PlatformProject.Screenshot> {
        return gallery.map { gallery ->
            PlatformProject.Screenshot(
                imageUrl = gallery.url,
                title = gallery.title,
                description = gallery.description
            )
        }
    }
}

fun ModrinthSingleProject.isPublic(): Boolean {
    return when (this.status) {
        "approved", "archived" -> true
        else -> false
    }
}
