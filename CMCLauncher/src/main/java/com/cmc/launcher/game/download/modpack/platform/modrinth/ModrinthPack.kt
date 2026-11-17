package com.cmc.launcher.game.download.modpack.platform.modrinth

import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.addon.platform.mcim.mapMCIMMirrorUrls
import com.cmc.launcher.game.download.modpack.install.ModFile
import com.cmc.launcher.game.download.modpack.install.ModPackInfo
import com.cmc.launcher.game.download.modpack.install.ModPackInfoTask
import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import java.io.File

class ModrinthPack(
    root: File,
    private val manifest: ModrinthManifest
) : ModPackInfoTask(
    root = root,
    platform = PackPlatform.Modrinth
) {

    suspend fun readModrinth(
        task: Task,
        targetFolder: File,
        extractFiles: suspend (internalPath: String, outputDir: File) -> Unit
    ): ModPackInfo {

        val files = manifest.files.mapNotNull { manifestFile ->

            if (manifestFile.env?.client == "unsupported") return@mapNotNull null
            ModFile(
                outputFile = File(targetFolder, manifestFile.path),
                downloadUrls = manifestFile.downloads.mapMCIMMirrorUrls(),
                sha1 = manifestFile.hashes.sha1
            )
        }

        val loaders = manifest.dependencies.entries.mapNotNull { (id, version) ->
            when (id) {
                "forge" -> ModLoader.FORGE to version
                "neoforge" -> ModLoader.NEOFORGE to version
                "fabric-loader" -> ModLoader.FABRIC to version
                "quilt-loader" -> ModLoader.QUILT to version
                else -> null
            }
        }

        task.updateProgress(-1f)
        task.updateMessage(androidText(R.string.download_modpack_install_overrides))
        extractFiles("overrides", targetFolder)
        extractFiles("client-overrides", targetFolder)

        return ModPackInfo(
            name = manifest.name,
            summary = manifest.summary,
            files = files,
            loaders = loaders,
            gameVersion = manifest.getGameVersion()
        )
    }

    override suspend fun readInfo(
        task: Task,
        versionFolder: File,
        root: File
    ): ModPackInfo {
        return readModrinth(
            task = task,
            targetFolder = versionFolder,
            extractFiles = { internalPath, outputDir ->
                val sourceDir = internalPath.takeIf { it.isNotBlank() }
                    ?.let { File(root, it) }
                    ?: root
                if (sourceDir.exists()) {

                    copyDirectoryContents(
                        from = sourceDir,
                        to = outputDir,
                        onProgress = { progress ->
                            task.updateProgress(progress)
                        }
                    )
                }
            }
        )
    }
}
