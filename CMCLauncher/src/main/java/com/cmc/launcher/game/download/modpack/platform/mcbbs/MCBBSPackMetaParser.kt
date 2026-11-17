package com.cmc.launcher.game.download.modpack.platform.mcbbs

import com.cmc.launcher.game.download.modpack.platform.AbstractPack
import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.game.download.modpack.platform.SimplePackParser
import com.cmc.launcher.utils.logging.Logger
import java.io.File

private const val TAG = "MCBBSPackMetaParser"

object MCBBSPackMetaParser : SimplePackParser<MCBBSManifest>(
    indexFilePath = "mcbbs.packmeta",
    manifestClass = MCBBSManifest::class.java,
    buildPack = { root, manifest ->
        if (!manifest.checkMCBBSManifest()) {
            error("This MCBBS modpack does not provide game version information and cannot be installed!")
        }
        MCBBSPack(root, manifest)
    }
) {
    override suspend fun parse(packFolder: File): AbstractPack? {
        val result = runCatching {
            super.parse(packFolder)
        }.onFailure { th ->
            Logger.debug(TAG, "${getIdentifier()}: Failed to parse the modpack using \"mcbbs.packmeta\", trying \"manifest.json\" instead.", th)
        }.getOrNull() ?: run {

            MCBBSPackManifestParser.parse(packFolder)
        }
        return result
    }

    override fun getIdentifier(): String = PackPlatform.MCBBS.identifier

    private object MCBBSPackManifestParser : SimplePackParser<MCBBSManifest>(
        indexFilePath = "manifest.json",
        manifestClass = MCBBSManifest::class.java,
        buildPack = { root, manifest ->
            if (!manifest.checkMCBBSManifest()) {
                error("This MCBBS modpack does not provide game version information and cannot be installed!")
            }
            MCBBSPack(root, manifest)
        }
    ) {
        override fun getIdentifier(): String = PackPlatform.MCBBS.identifier
    }
}

private fun MCBBSManifest.checkMCBBSManifest(): Boolean {

    return addons.any { it.id == "game" }
}
