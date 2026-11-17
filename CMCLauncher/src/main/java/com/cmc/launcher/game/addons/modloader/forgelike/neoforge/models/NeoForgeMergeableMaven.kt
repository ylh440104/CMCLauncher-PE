package com.cmc.launcher.game.addons.modloader.forgelike.neoforge.models

import com.cmc.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion

interface NeoForgeMergeableMaven<E>  {

    operator fun plus(maven: E): List<NeoForgeVersion>

    fun isVersionInvalid(versionId: String): Boolean {
        val cantDownload = versionId == "47.1.82"
        val isAlpha = versionId.contains("-alpha")
        return cantDownload || isAlpha
    }
}
