package com.cmc.launcher.game.download.addon.platform

import com.cmc.launcher.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlatformSortField(
    val curseforge: String,
    val modrinth: String
): PlatformFilterCode {

    @SerialName("RELEVANCE")
    RELEVANCE("1", "relevance") {
        override fun getDisplayName(): Int = R.string.download_addon_filter_sort_by_relevant
    },

    @SerialName("DOWNLOADS")
    DOWNLOADS("6", "downloads") {
        override fun getDisplayName(): Int = R.string.download_addon_filter_sort_by_total_downloads
    },

    @SerialName("POPULARITY")
    POPULARITY("2", "follows") {
        override fun getDisplayName(): Int = R.string.download_addon_filter_sort_by_popularity
    },

    @SerialName("NEWEST")
    NEWEST("11", "newest") {
        override fun getDisplayName(): Int = R.string.download_addon_filter_sort_by_recently_created
    },

    @SerialName("UPDATED")
    UPDATED("3", "updated") {
        override fun getDisplayName(): Int = R.string.download_addon_filter_sort_by_recently_updated
    };

    override fun index(): Int = this.ordinal
}
