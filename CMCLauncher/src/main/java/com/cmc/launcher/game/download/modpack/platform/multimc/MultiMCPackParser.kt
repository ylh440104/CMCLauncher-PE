package com.cmc.launcher.game.download.modpack.platform.multimc

import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.game.download.modpack.platform.SimplePackParser

object MultiMCPackParser : SimplePackParser<MultiMCManifest>(
    indexFilePath = "mmc-pack.json",
    manifestClass = MultiMCManifest::class.java,
    buildPack = { root, manifest ->

        if (manifest.getMinecraftVersion() == null || manifest.components.isEmpty()) {
            error("This MMC modpack does not provide game version information and cannot be installed!")
        }

        MultiMCPack(root = root, manifest = manifest)
    }
) {
    override fun getIdentifier(): String = PackPlatform.MultiMC.identifier
}
