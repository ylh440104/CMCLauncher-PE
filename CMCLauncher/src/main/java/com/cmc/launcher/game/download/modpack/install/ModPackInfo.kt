package com.cmc.launcher.game.download.modpack.install

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.cmc.launcher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.cmc.launcher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.cmc.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.cmc.launcher.game.download.game.GameDownloadInfo

data class ModPackInfo(
    val name: String,
    val summary: String? = null,
    val ram: Int? = null,
    val files: List<ModFile>,
    val loaders: List<Pair<ModLoader, String>>,
    val gameVersion: String
)

suspend fun ModPackInfo.retrieveLoaderTask(
    targetVersionName: String
): GameDownloadInfo {
    var gameInfo = GameDownloadInfo(
        gameVersion = gameVersion,
        customVersionName = targetVersionName
    )

    loaders.forEach { pair ->
        pair.retrieveLoader(
            gameVersion = gameVersion,
            gameInfo = gameInfo,
            pasteGameInfo = { newInfo ->
                gameInfo = newInfo
            }
        )
    }

    return gameInfo
}

suspend fun Pair<ModLoader, String>.retrieveLoader(
    gameVersion: String,
    gameInfo: GameDownloadInfo,
    pasteGameInfo: (GameDownloadInfo) -> Unit
) {
    val (loader, version) = this
    when (loader) {
        ModLoader.FORGE -> {
            ForgeVersions.fetchForgeList(gameVersion)?.find {
                it.versionName == version
            }?.let { forgeVersion ->
                pasteGameInfo(gameInfo.copy(forge = forgeVersion))
            }
        }
        ModLoader.NEOFORGE -> {
            NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion)?.find {
                it.versionName == version
            }?.let { neoforgeVersion ->
                pasteGameInfo(gameInfo.copy(neoforge = neoforgeVersion))
            }
        }
        ModLoader.FABRIC -> {
            FabricVersions.fetchFabricLoaderList(gameVersion)?.find {
                it.version == version
            }?.let { fabricVersion ->
                pasteGameInfo(gameInfo.copy(fabric = fabricVersion))
            }
        }
        ModLoader.QUILT -> {
            QuiltVersions.fetchQuiltLoaderList(gameVersion)?.find {
                it.version == version
            }?.let { quiltVersion ->
                pasteGameInfo(gameInfo.copy(quilt = quiltVersion))
            }
        }
        else -> {

        }
    }
}
