package com.cmc.launcher.game.download.addon.platform.modrinth.models

import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformDependencyType
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformReleaseType
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.utils.string.parseInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant

@Serializable
class ModrinthVersion(

    @SerialName("name")
    val name: String,

    @SerialName("version_number")
    val versionNumber: String,

    @Transient
    @SerialName("changelog")
    @Deprecated("不应该被代码使用！")
    val changelog: String? = null,

    @SerialName("dependencies")
    val dependencies: Array<Dependency>,

    @SerialName("game_versions")
    val gameVersions: Array<String>,

    @SerialName("version_type")
    val versionType: PlatformReleaseType,

    @SerialName("loaders")
    val loaders: Array<String>,

    @SerialName("featured")
    val featured: Boolean,

    @SerialName("status")
    val status: String,

    @SerialName("requested_status")
    val requestedStatus: String? = null,

    @SerialName("id")
    val id: String,

    @SerialName("project_id")
    val projectId: String,

    @SerialName("author_id")
    val authorId: String,

    @SerialName("date_published")
    val datePublished: String,

    @SerialName("downloads")
    val downloads: Long,

    @SerialName("changelog_url")
    val changelogUrl: String? = null,

    @SerialName("files")
    val files: Array<ModrinthFile>
) : PlatformVersion {
    @Serializable
    class Dependency(

        @SerialName("version_id")
        val versionId: String? = null,

        @SerialName("project_id")
        val projectId: String? = null,

        @SerialName("file_name")
        val fileName: String? = null,

        @SerialName("dependency_type")
        val dependencyType: PlatformDependencyType
    )

    @Transient
    private lateinit var thisPrimaryFile: ModrinthFile

    override suspend fun initFile(currentProjectId: String): Boolean {
        val file = files.getPrimary() ?: return false
        thisPrimaryFile = file
        return true
    }

    override fun platform(): Platform = Platform.MODRINTH

    override fun platformId(): String = id

    override fun platformProjectId(): String = projectId

    override fun platformDisplayName(): String = name

    override fun platformFileName(): String = thisPrimaryFile.fileName

    override fun platformGameVersion(): Array<String> = gameVersions

    override fun platformLoaders(): List<PlatformDisplayLabel> = loaders.mapNotNull { loaderName ->
        ModrinthModLoaderCategory.entries.find { category ->
            category.facetValue() == loaderName
        }
    }

    override fun platformReleaseType(): PlatformReleaseType = versionType

    override fun platformDependencies(): List<PlatformVersion.PlatformDependency> = dependencies.mapNotNull { dependency ->
        if (dependency.projectId == null && dependency.versionId == null) return@mapNotNull null
        PlatformVersion.PlatformDependency(
            platform = platform(),
            projectId = dependency.projectId,
            versionId = dependency.versionId,
            type = dependency.dependencyType
        )
    }

    override fun platformDownloadCount(): Long = downloads

    override fun platformDownloadUrl(): String = thisPrimaryFile.url

    override fun platformDatePublished(): Instant = parseInstant(datePublished)

    override fun platformSha1(): String? = thisPrimaryFile.hashes.sha1

    override fun platformFileSize(): Long = thisPrimaryFile.size

    override fun platformVersion(): String = versionNumber
}
