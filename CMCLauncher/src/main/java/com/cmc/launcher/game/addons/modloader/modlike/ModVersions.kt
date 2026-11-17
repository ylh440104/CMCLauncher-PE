package com.cmc.launcher.game.addons.modloader.modlike

import com.cmc.launcher.game.download.addon.platform.mirroredModrinthSource
import com.cmc.launcher.game.download.addon.platform.mirroredPlatformSearcher
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthVersion
import com.cmc.launcher.game.download.addon.platform.modrinth.models.getPrimary
import com.cmc.launcher.ui.screens.content.download.addon.elements.initAllGeneric
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException

private const val TAG = "ModVersions"

abstract class ModVersions(
    private val modrinthID: String
) {
    private var cacheVersions: List<ModrinthVersion>? = null

    suspend fun fetchVersionList(
        mcVersion: String,
        force: Boolean = false
    ): List<ModVersion>? {
        try {
            val versions = run {
                if (!force && cacheVersions != null) return@run cacheVersions!!
                mirroredPlatformSearcher(
                    searchers = mirroredModrinthSource()
                ) { searcher ->
                    searcher.getVersions(
                        projectID = modrinthID
                    ).initAllGeneric(
                        currentProjectId = modrinthID
                    )
                }.also {
                    cacheVersions = it
                }
            }

            return versions.mapNotNull { version ->

                if (!version.gameVersions.contains(mcVersion)) return@mapNotNull null

                val file = version.files.getPrimary() ?: run {
                    Logger.warning(TAG, "No file list available, skipping -> ${version.name}")
                    return@mapNotNull null
                }
                ModVersion(
                    inherit = mcVersion,
                    displayName = version.versionNumber,
                    version = version,
                    file = file
                )
            }
        } catch (_: CancellationException) {
            Logger.debug(TAG, "Client cancelled.")
            return null
        } catch (e: Exception) {
            Logger.debug(TAG, "Failed to fetch mod list! {mod id = $modrinthID}", e)
            throw e
        }
    }
}
