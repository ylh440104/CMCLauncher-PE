package com.cmc.launcher.game.download.modpack.platform

import com.cmc.launcher.game.download.modpack.platform.curseforge.CurseForgePackParser
import com.cmc.launcher.game.download.modpack.platform.mcbbs.MCBBSPackMetaParser
import com.cmc.launcher.game.download.modpack.platform.modrinth.ModrinthPackParser
import com.cmc.launcher.game.download.modpack.platform.multimc.MultiMCPackParser
import java.io.File

interface PackParser {

    suspend fun parse(packFolder: File): AbstractPack?

    fun getIdentifier(): String
}

val ALL_PACK_PARSER = listOf(
    CurseForgePackParser,
    ModrinthPackParser,
    MultiMCPackParser,
    MCBBSPackMetaParser
)
