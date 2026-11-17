package com.cmc.launcher.game.download.modpack.platform.curseforge

import com.google.gson.JsonParseException
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.addon.platform.curseforge.models.fixedFileUrl
import com.cmc.launcher.game.download.addon.platform.curseforge.models.getPlatformClassesOrNull
import com.cmc.launcher.game.download.addon.platform.curseforge.models.getSHA1
import com.cmc.launcher.game.download.addon.platform.mcim.mapMCIMMirrorUrls
import com.cmc.launcher.game.download.addon.platform.mirroredCurseForgeSource
import com.cmc.launcher.game.download.addon.platform.mirroredPlatformSearcher
import com.cmc.launcher.game.download.modpack.install.ModFile
import com.cmc.launcher.game.download.modpack.install.ModPackInfo
import com.cmc.launcher.game.download.modpack.install.ModPackInfoTask
import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import com.cmc.launcher.utils.logging.Logger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "CurseForgePack"

class CurseForgePack(
    root: File,
    private val manifest: CurseForgeManifest
) : ModPackInfoTask(
    root = root,
    platform = PackPlatform.CurseForge
) {

    suspend fun readCurseForge(
        task: Task,
        targetFolder: File,
        extractFiles: suspend (internalPath: String, outputDir: File) -> Unit
    ): ModPackInfo {
        val modsFolder = VersionFolders.MOD.getDir(targetFolder)

        val totalCount = manifest.files.size
        val files = manifest.files.mapIndexed { index, manifestFile ->
            val modFile = if (manifestFile.fileName.isNullOrBlank() || manifestFile.getFileUrl() == null) {
                ModFile(
                    getFile = {
                        runCatching {
                            val version = mirroredPlatformSearcher(
                                searchers = mirroredCurseForgeSource()
                            ) { searcher ->
                                searcher.getVersion(
                                    projectID = manifestFile.projectID.toString(),
                                    fileID = manifestFile.fileID.toString()
                                )
                            }.data
                            val url = version.fixedFileUrl() ?: throw IOException("Can't get the file url")
                            val fileName = version.fileName ?: throw IOException("Can't get the file name")

                            val project = mirroredPlatformSearcher(
                                searchers = mirroredCurseForgeSource()
                            ) { searcher ->
                                searcher.getProject(
                                    projectID = manifestFile.projectID.toString()
                                )
                            }.data

                            val folder = project.getPlatformClassesOrNull()
                                ?.versionFolder?.folderName
                                ?.let { folderName ->
                                    File(targetFolder, folderName)
                                } ?: modsFolder

                            ModFile(
                                outputFile = File(folder, fileName),
                                downloadUrls = url.mapMCIMMirrorUrls(),
                                sha1 = version.getSHA1()
                            )
                        }.onFailure { e ->
                            when (e) {
                                is FileNotFoundException -> Logger.warning(TAG, "Could not query api.curseforge.com for deleted mods: ${manifestFile.projectID}, ${manifestFile.fileID}", e)
                                is IOException, is JsonParseException -> Logger.warning(TAG, "Unable to fetch the file name projectID=${manifestFile.projectID}, fileID=${manifestFile.fileID}", e)
                                else -> Logger.warning(TAG, "Unable to fetch the file name projectID=${manifestFile.projectID}, fileID=${manifestFile.fileID}", e)
                            }
                        }.getOrThrow()
                    }
                )
            } else {
                ModFile(
                    outputFile = File(modsFolder, manifestFile.fileName),
                    downloadUrls = listOf(manifestFile.getFileUrl()!!)
                )
            }
            task.updateProgress(
                index.toFloat() / totalCount.toFloat()
            )
            task.updateMessage(
                androidText(
                    R.string.download_modpack_install_get_mod_url,
                    index, totalCount
                )
            )
            modFile
        }

        val loaders = manifest.minecraft.modLoaders.mapNotNull { modloader ->
            val id = modloader.id
            when {
                id.startsWith("forge-") -> ModLoader.FORGE to id.removePrefix("forge-")
                id.startsWith("fabric-") -> ModLoader.FABRIC to id.removePrefix("fabric-")
                id.startsWith("neoforge-") -> ModLoader.NEOFORGE to id.removePrefix("neoforge-")
                id.startsWith("quilt-") -> ModLoader.QUILT to id.removePrefix("quilt-")
                else -> null
            }
        }

        task.updateProgress(-1f)
        task.updateMessage(androidText(R.string.download_modpack_install_overrides))
        extractFiles(manifest.overrides ?: "overrides", targetFolder)

        return ModPackInfo(
            name = manifest.name,
            ram = manifest.minecraft.recommendedRam,
            files = files,
            loaders = loaders,
            gameVersion = manifest.minecraft.gameVersion
        )
    }

    override suspend fun readInfo(
        task: Task,
        versionFolder: File,
        root: File
    ): ModPackInfo {
        return readCurseForge(
            task = task,
            targetFolder = versionFolder,
            extractFiles = { internal, out ->
                val sourceDir = internal.takeIf { it.isNotBlank() }
                    ?.let { File(root, it) }
                    ?: root

                copyDirectoryContents(
                    from = sourceDir,
                    to = out,
                    onProgress = { progress ->
                        task.updateProgress(progress)
                    }
                )
            }
        )
    }
}
