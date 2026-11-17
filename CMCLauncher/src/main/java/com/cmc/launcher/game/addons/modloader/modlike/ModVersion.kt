package com.cmc.launcher.game.addons.modloader.modlike

import com.cmc.launcher.game.addons.modloader.AddonVersion
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthFile
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthVersion

open class ModVersion(

    inherit: String,

    val displayName: String,

    val version: ModrinthVersion,

    val file: ModrinthFile
) : AddonVersion(
    inherit = inherit
) {

    override fun getAddonVersion(): String = this.version.versionNumber

    override fun isVersion(versionString: String): Boolean {
        return this.version.versionNumber == versionString
    }
}
