package com.cmc.launcher.game.version.download

import com.cmc.launcher.game.addons.mirror.mapBMCLMirrorUrls
import com.cmc.launcher.game.path.getAssetsHome
import com.cmc.launcher.game.path.getGameHome
import com.cmc.launcher.game.path.getLibrariesHome
import com.cmc.launcher.game.path.getResourcesHome
import com.cmc.launcher.game.path.getVersionsHome
import com.cmc.launcher.game.versioninfo.MinecraftVersions
import com.cmc.launcher.game.versioninfo.models.AssetIndexJson
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.game.versioninfo.models.VersionManifest.Version
import com.cmc.launcher.utils.file.ensureDirectory
import com.cmc.launcher.utils.file.ensureParentDirectory
import com.cmc.launcher.utils.string.isNotEmptyOrBlank
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

const val DOWNLOADER_TAG = "MinecraftDownloader"
const val MINECRAFT_RES: String = "https://resources.download.minecraft.net/"

class BaseMinecraftDownloader(
    gameHome: String = getGameHome()
) {

    val assetsTarget = File(getAssetsHome(gameHome)).ensureDirectory()
    val resourcesTarget = File(getResourcesHome(gameHome)).ensureDirectory()
    val versionsTarget = File(getVersionsHome(gameHome)).ensureDirectory()
    val librariesTarget = File(getLibrariesHome(gameHome)).ensureDirectory()
    val assetIndexTarget = File(assetsTarget, "indexes").ensureDirectory()

    suspend fun findVersion(version: String): Version? {
        val versionManifest = MinecraftVersions.getVersionManifest()
        return versionManifest.versions.find { it.id == version }
    }

    fun getVersionJsonPath(version: String, mcFolder: File = versionsTarget) =
        File(mcFolder, "$version/$version.json".replace("/", File.separator)).ensureParentDirectory()

    fun getVersionJarPath(version: String, mcFolder: File = versionsTarget) =
        File(mcFolder, "$version/$version.jar".replace("/", File.separator)).ensureParentDirectory()

    suspend fun createVersionJson(version: Version): GameManifest {
        return createVersionJson(version, version.id)
    }

    suspend fun createVersionJson(
        version: Version,
        targetVersion: String,
        mcFolder: File = versionsTarget
    ): GameManifest {
        return downloadAndParseJson(
            targetFile = getVersionJsonPath(targetVersion, mcFolder),
            url = version.url,
            expectedSHA = version.sha1,
            classOfT = GameManifest::class.java
        )
    }

    suspend fun createAssetIndex(
        assetIndexTarget: File,
        gameManifest: GameManifest
    ): AssetIndexJson? {
        val indexFile = File(assetIndexTarget, "${gameManifest.assets}.json")
        return gameManifest.assetIndex?.let { assetIndex ->
            downloadAndParseJson(
                targetFile = indexFile,
                url = assetIndex.url,
                expectedSHA = assetIndex.sha1,
                classOfT = AssetIndexJson::class.java
            )
        }
    }

    fun loadClientJarDownload(
        gameManifest: GameManifest,
        clientName: String,
        mcFolder: File = versionsTarget,
        scheduleDownload: (urls: List<String>, hash: String?, targetFile: File, size: Long) -> Unit,
        scheduleCopy: (targetFile: File) -> Unit
    ) {
        val clientFile = getVersionJarPath(clientName, mcFolder)
        gameManifest.downloads?.client
            ?.takeIf { it.url?.isNotEmptyOrBlank() == true }
            ?.let { client ->
                scheduleDownload(client.url.mapBMCLMirrorUrls(), client.sha1, clientFile, client.size)
            } ?: run {

                scheduleCopy(clientFile)
            }
    }

    suspend fun loadAssetsDownload(
        assetIndex: AssetIndexJson?,
        resourcesTargetDir: File = resourcesTarget,
        assetsTargetDir: File = assetsTarget,
        scheduleDownload: (urls: List<String>, hash: String?, targetFile: File, size: Long) -> Unit
    ) {
        assetIndex?.objects?.forEach { (path, objectInfo) ->
            currentCoroutineContext().ensureActive()

            val hashedPath = "${objectInfo.hash.substring(0, 2)}/${objectInfo.hash}"
            val targetPath = if (assetIndex.isMapToResources) resourcesTargetDir else assetsTargetDir
            val targetFile: File = if (assetIndex.isVirtual || assetIndex.isMapToResources) {
                File(targetPath, path)
            } else {
                File(targetPath, "objects/${hashedPath}".replace("/", File.separator))
            }
            scheduleDownload("$MINECRAFT_RES$hashedPath".mapBMCLMirrorUrls(), objectInfo.hash, targetFile, objectInfo.size)
        }
    }

    suspend fun loadLibraryDownloads(
        gameManifest: GameManifest,
        targetDir: File = librariesTarget,
        scheduleDownload: (urls: List<String>, hash: String?, targetFile: File, size: Long, isDownloadable: Boolean) -> Unit
    ) {
        gameManifest.libraries?.let { libraries ->
            processLibraries { libraries }
            libraries.forEach { library ->
                currentCoroutineContext().ensureActive()

                if (library.name.startsWith("org.lwjgl")) return@forEach

                val artifactPath: String = artifactToPath(library) ?: return@forEach

                val artifact = library.downloads?.let { it.artifact ?: return@forEach }
                val sha1: String? = artifact?.sha1 ?: library.sha1
                val size: Long = artifact?.size ?: library.size

                var isDownloadable = true
                val url: String = artifact?.url
                    ?.takeIf { it.isNotEmptyOrBlank() }
                    ?: library.url
                        ?.takeIf {

                            it.isNotEmptyOrBlank()
                        }
                        ?.replace("http://", "https://")
                        ?.let { base -> "${base}$artifactPath" }
                    ?: run {

                        isDownloadable = false
                        "https://libraries.minecraft.net/$artifactPath"
                    }

                scheduleDownload(url.mapBMCLMirrorUrls(), sha1, File(targetDir, artifactPath), size, isDownloadable)
            }
        }
    }

}
