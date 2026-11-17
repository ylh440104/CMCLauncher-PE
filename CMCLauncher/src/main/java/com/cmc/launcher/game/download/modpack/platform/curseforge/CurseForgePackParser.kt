package com.cmc.launcher.game.download.modpack.platform.curseforge

import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.game.download.modpack.platform.SimplePackParser
import com.cmc.launcher.game.download.modpack.platform.mcbbs.MCBBSManifest
import com.cmc.launcher.game.download.modpack.platform.mcbbs.MCBBSPackMetaParser
import com.cmc.launcher.game.download.modpack.platform.multimc.MultiMCManifest
import com.cmc.launcher.game.download.modpack.platform.multimc.MultiMCPackParser
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.logging.Logger
import java.io.File

private const val TAG = "CurseForgePackParser"

object CurseForgePackParser : SimplePackParser<CurseForgeManifest>(
    indexFilePath = "manifest.json",
    manifestClass = CurseForgeManifest::class.java,
    extraProcess = extraProcess@{ root ->

        val mccManifest = File(root, MultiMCPackParser.indexFilePath)
        if (mccManifest.exists()) {
            try {
                GSON.fromJson(mccManifest.readText(), MultiMCManifest::class.java)

                return@extraProcess false
            } catch (th: Throwable) {
                Logger.warning(TAG, "An exception occurred while trying to exclude the MultiMC modpack.", th)
            }
        }

        val mcbbsMeta = File(root, MCBBSPackMetaParser.indexFilePath)
        if (mcbbsMeta.exists()) {
            try {
                GSON.fromJson(mcbbsMeta.readText(), MCBBSManifest::class.java)

                return@extraProcess false
            } catch (th: Throwable) {
                Logger.warning(TAG, "An exception occurred while trying to exclude the MCBBS modpack.", th)
            }
        }
        true
    },
    buildPack = { root, manifest ->
        CurseForgePack(root = root, manifest = manifest)
    }
) {
    override fun getIdentifier(): String = PackPlatform.CurseForge.identifier
}
