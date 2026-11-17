package com.cmc.launcher.game.download.addon.platform.curseforge

import com.cmc.launcher.game.download.addon.platform.AbstractPlatformSearcher
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformSearchFilter
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeFile
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeFingerprintsMatches
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeProject
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeVersion
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeVersions
import com.cmc.launcher.game.download.addon.platform.curseforge.models.isApproved
import com.cmc.launcher.utils.file.MurmurHash2Incremental
import com.cmc.launcher.utils.network.httpGetJson
import com.cmc.launcher.utils.network.httpPostJson
import io.ktor.http.Parameters
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class CurseForgeSearcher(
    val api: String = CURSEFORGE_API,
    source: String = "Official CurseForge"
): AbstractPlatformSearcher(
    platform = Platform.CURSEFORGE,
    source = source
) {
    override suspend fun searchAddons(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): CurseForgeSearchResult {
        return httpGetJson(
            url = "$api/mods/search",
            parameters = searchFilter.toCurseForgeRequest(
                query = query,
                platformClasses = platformClasses
            ).toParameters()
        )
    }

    override suspend fun getProject(projectID: String): CurseForgeProject {
        val project = httpGetJson<CurseForgeProject>(
            url = "$api/mods/$projectID"
        )
        if (!project.isApproved()) throw NotFoundException("The project {$projectID} is not in a publicly available state.")
        return project
    }

    suspend fun getVersion(
        projectID: String,
        fileID: String,
    ): CurseForgeVersion {
        return httpGetJson(
            url = "$api/mods/$projectID/files/$fileID"
        )
    }

    suspend fun getVersions(
        projectID: String,
        index: Int = 0,
        pageSize: Int = 100
    ): CurseForgeVersions = httpGetJson(
        url = "$api/mods/$projectID/files",
        parameters = Parameters.build {
            append("index", index.toString())
            append("pageSize", pageSize.toString())
        }
    )

    override suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit
    ): List<CurseForgeFile> {
        return getAllVersions(
            pageSize = 50,
            chunkSize = 20,
            maxConcurrent = 10,
            pageCallback = pageCallback,
            checkNotEmpty = { versions ->
                versions.data.isNotEmpty()
            },
            asyncVersions = { index, pageSize ->
                getVersions(
                    projectID = projectID,
                    index = index,
                    pageSize = pageSize,
                )
            },
            processVersions = { versions ->
                val files = versions?.data ?: emptyArray()
                files.toList() to files.size
            }
        )
    }

    override suspend fun getVersionByLocalFile(
        file: File,
        sha1: String
    ): CurseForgeFile? {
        val hash = MurmurHash2Incremental.computeHash(file, byteToSkip = listOf(0x9, 0xa, 0xd, 0x20))
        return httpPostJson<CurseForgeFingerprintsMatches>(
            url = "$api/fingerprints",
            body = mapOf("fingerprints" to listOf(hash))
        ).data.exactMatches
            ?.takeIf { it.isNotEmpty() }
            ?.firstOrNull()
            ?.file
    }

    suspend fun getFilesByFingerprints(
        fingerprints: List<Long>
    ): Map<Long, CurseForgeFile> {
        if (fingerprints.isEmpty()) return emptyMap()
        val matches = httpPostJson<CurseForgeFingerprintsMatches>(
            url = "$api/fingerprints",
            body = CurseForgeFingerprintsRequest(fingerprints = fingerprints)
        )
        return matches.data.exactMatches.orEmpty()
            .associate { it.file.fileFingerprint to it.file }
    }
}

@Serializable
private data class CurseForgeFingerprintsRequest(
    @SerialName("fingerprints")
    val fingerprints: List<Long>
)

private suspend fun <E, T> getAllVersions(
    pageSize: Int = 100,
    chunkSize: Int = 10,
    maxConcurrent: Int = 5,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _ , _ -> },
    checkNotEmpty: (E) -> Boolean,
    asyncVersions: suspend (index: Int, pageSize: Int) -> E,
    processVersions: suspend (E?) -> Pair<List<T>, Int>
): List<T> = withContext(Dispatchers.IO) {
    coroutineScope {
        val allVersions = mutableListOf<T>()

        var currentChunk = 1

        var startPage = 0

        var reachedEnd = false

        val semaphore = Semaphore(maxConcurrent)

        while (!reachedEnd) {

            val jobs = (0 until chunkSize).map { offset ->
                val pageIndex = startPage + offset
                val index = pageIndex * pageSize

                async {
                    semaphore.withPermit {
                        val response = asyncVersions(index, pageSize)

                        if (checkNotEmpty(response)) {

                            pageCallback(currentChunk, pageIndex + 1)
                            response
                        } else null
                    }
                }
            }

            for ((i, job) in jobs.withIndex()) {
                val (files, realSize) = processVersions(job.await())
                files.takeIf { it.isNotEmpty() }?.let { list ->
                    allVersions.addAll(list)
                }

                if (realSize < pageSize) {
                    reachedEnd = true

                    for (j in (i + 1) until jobs.size) {
                        jobs[j].cancel()
                    }
                    break
                }
            }

            if (!reachedEnd) {
                startPage += chunkSize
                currentChunk++
            }
        }

        return@coroutineScope allVersions
    }
}
