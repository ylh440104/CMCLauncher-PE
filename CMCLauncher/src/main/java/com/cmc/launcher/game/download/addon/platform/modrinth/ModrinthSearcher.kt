package com.cmc.launcher.game.download.addon.platform.modrinth

import com.cmc.launcher.game.download.addon.platform.AbstractPlatformSearcher
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformSearchFilter
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthSingleProject
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthVersion
import com.cmc.launcher.utils.network.httpGetJson
import com.cmc.launcher.utils.network.httpPostJson
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class ModrinthSearcher(
    val api: String = MODRINTH_API,
    source: String = "Official Modrinth"
): AbstractPlatformSearcher(
    platform = Platform.MODRINTH,
    source = source
) {
    override suspend fun searchAddons(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): ModrinthSearchResult {
        return httpGetJson(
            url = "$api/search",
            parameters = searchFilter.toModrinthRequest(
                query = query,
                platformClasses = platformClasses
            ).toParameters()
        )
    }

    override suspend fun getProject(projectID: String): ModrinthSingleProject {
        val project = httpGetJson<ModrinthSingleProject>(
            url = "$api/project/$projectID"
        )

        return project
    }

    suspend fun getVersionsChunk(
        projectID: String,
        pageSize: Int? = null,
        offset: Int? = null,
    ): List<ModrinthVersion> {
        return httpGetJson(
            url = "$api/project/$projectID/version",
            parameters = if (pageSize != null && offset != null) {
                Parameters.build {
                    append("limit", pageSize.toString())
                    append("offset", offset.toString())
                    append("include_changelog", "false")
                }
            } else null
        )
    }

    override suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit
    ): List<ModrinthVersion> {
        return getVersionsChunk(
            projectID = projectID
        )
    }

    suspend fun getVersion(versionID: String): ModrinthVersion {
        return httpGetJson(
            url = "$api/version/$versionID"
        )
    }

    override suspend fun getVersionByLocalFile(
        file: File,
        sha1: String
    ): ModrinthVersion? {
        return try {
            httpGetJson(
                url = "$api/version_file/$sha1",
                parameters = Parameters.build {
                    append("algorithm", "sha1")
                }
            )
        } catch (_: ClientRequestException) {
            null
        }
    }

    suspend fun getVersionFiles(
        sha1List: List<String>
    ): Map<String, ModrinthVersion> {
        if (sha1List.isEmpty()) return emptyMap()
        return httpPostJson(
            url = "$api/version_files",
            body = ModrinthVersionFilesRequest(hashes = sha1List)
        )
    }
}

@Serializable
private data class ModrinthVersionFilesRequest(
    @SerialName("hashes")
    val hashes: List<String>,
    @SerialName("algorithm")
    val algorithm: String = "sha1"
)
