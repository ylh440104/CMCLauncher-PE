package com.cmc.launcher.game.download.addon.platform

interface PlatformProject {

    fun platform(): Platform

    fun platformId(): String

    fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses

    fun platformSlug(): String

    fun platformIconUrl(): String?

    fun platformTitle(): String

    fun platformSummary(): String

    fun platformAuthors(): List<String>?

    fun platformDownloadCount(): Long

    fun platformFollows(): Long?

    fun platformModLoaders(): List<PlatformDisplayLabel>?

    fun platformAvailable(): Boolean = true

    fun checkClasses()

    fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>?

    fun platformUrls(defaultClasses: PlatformClasses): Urls

    fun platformScreenshots(): List<Screenshot>

    class Urls(
        val projectUrl: String? = null,
        val sourceUrl: String? = null,
        val issuesUrl: String? = null,
        val wikiUrl: String? = null
    )

    class Screenshot(
        val imageUrl: String,
        val title: String? = null,
        val description: String? = null
    )
}

fun PlatformProject.Urls.isAllNull(): Boolean {
    return projectUrl == null &&
            sourceUrl == null &&
            issuesUrl == null &&
            wikiUrl   == null
}
