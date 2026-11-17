package com.cmc.launcher.game.download.addon.platform

interface PlatformSearchData {

    fun platform(): Platform

    fun platformId(): String

    fun platformTitle(): String

    fun platformDescription(): String

    fun platformAuthors(): List<String>?

    fun platformIconUrl(): String?

    fun platformDownloadCount(): Long

    fun platformFollows(): Long?

    fun platformModLoaders(): List<PlatformDisplayLabel>?

    fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>?
}
