package com.cmc.launcher.game.version.mod.update

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.download.addon.platform.getVersions
import com.cmc.launcher.game.version.mod.ModFile
import com.cmc.launcher.game.version.mod.ModProject
import com.cmc.launcher.ui.screens.content.download.addon.elements.initAll
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.parseInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jackhuang.hmcl.addon.AddonProjectInfo
import java.io.File

private const val TAG = "ModData"

data class ModData(
    val file: File,
    val modFile: ModFile,
    val project: ModProject,
    val mcMod: AddonProjectInfo?
) {

    var currentVersion: String? = null
        private set

    suspend fun checkUpdate(
        minecraftVer: String,
        modLoader: ModLoader
    ): PlatformVersion? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val projectId = project.id
                val currentLoaderName = modLoader.displayName.lowercase()
                val currentFileLoaders = modFile.loaders
                    .map { it.getDisplayName().lowercase() }
                    .toSet()
                val targetLoaders = when {
                    currentLoaderName in currentFileLoaders -> {

                        setOf(currentLoaderName)
                    }

                    currentFileLoaders.isNotEmpty() -> {

                        currentFileLoaders
                    }

                    else -> {

                        setOf(currentLoaderName)
                    }
                }

                val versions = getVersions(
                    projectId,
                    project.platform
                ).initAll(projectId)

                val currentEntry = versions.firstOrNull { it.platformId() == modFile.id }
                if (currentEntry != null) {

                    currentVersion = currentEntry.platformVersion()
                }
                val datePublished = currentEntry?.platformDatePublished() ?: run {
                    Logger.debug(
                        TAG,
                        "Installed version ${modFile.id} not found in the fetched list of ${file.name}, falling back to the cached date"
                    )
                    parseInstant(modFile.datePublished)
                }

                val candidates = versions.filter { version ->
                    val loaderNames = version.platformLoaders()
                        .map { it.getDisplayName().lowercase() }
                        .toSet()

                    minecraftVer in version.platformGameVersion() &&

                    loaderNames.any { it in targetLoaders } &&

                    version.platformDatePublished() > datePublished
                }

                candidates.firstOrNull()?.also { version ->
                    Logger.info(TAG, "Detected update for mod ${file.name}: $currentVersion -> ${version.platformVersion()}")
                }
            }.onFailure { th ->
                Logger.warning(TAG, "An error occurred while fetching all versions of the mod.", th)
            }.getOrNull()
        }
    }
}
