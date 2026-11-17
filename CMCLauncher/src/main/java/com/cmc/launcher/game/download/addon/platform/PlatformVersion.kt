package com.cmc.launcher.game.download.addon.platform

import java.time.Instant

interface PlatformVersion {

    suspend fun initFile(currentProjectId: String): Boolean

    fun platform(): Platform

    fun platformId(): String

    fun platformProjectId(): String

    fun platformDisplayName(): String

    fun platformFileName(): String

    fun platformGameVersion(): Array<String>

    fun platformLoaders(): List<PlatformDisplayLabel>

    fun platformReleaseType(): PlatformReleaseType

    fun platformDependencies(): List<PlatformDependency>

    fun platformDownloadCount(): Long

    fun platformDownloadUrl(): String

    fun platformDatePublished(): Instant

    fun platformSha1(): String?

    fun platformFileSize(): Long

    fun platformVersion(): String

    class PlatformDependency(
        val platform: Platform,
        val projectId: String?,
        val versionId: String? = null,
        val type: PlatformDependencyType
    )
}

fun PlatformVersion.PlatformDependency.cacheKey(): String {
    return "${platform.name}/${projectId.orEmpty()}/${versionId.orEmpty()}"
}
