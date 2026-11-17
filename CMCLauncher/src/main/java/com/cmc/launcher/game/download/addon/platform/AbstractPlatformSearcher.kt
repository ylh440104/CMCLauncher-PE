package com.cmc.launcher.game.download.addon.platform

import java.io.File

abstract class AbstractPlatformSearcher(
    val platform: Platform,
    val source: String
) {

    abstract suspend fun searchAddons(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): PlatformSearchResult

    abstract suspend fun getProject(
        projectID: String,
    ): PlatformProject

    abstract suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit = { _, _ -> },
    ): List<PlatformVersion>

    abstract suspend fun getVersionByLocalFile(
        file: File,
        sha1: String,
    ): PlatformVersion?
}
