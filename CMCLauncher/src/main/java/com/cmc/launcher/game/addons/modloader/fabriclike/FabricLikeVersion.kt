package com.cmc.launcher.game.addons.modloader.fabriclike

import com.cmc.launcher.game.addons.modloader.AddonVersion

abstract class FabricLikeVersion(

    inherit: String,

    val loaderName: String,

    val version: String,

    val stable: Boolean = true
) : AddonVersion(
    inherit = inherit
) {
    abstract val loaderUrl: String

    val loaderJsonUrl: String =
        "$loaderUrl/${
            inherit.replace("∞", "infinite")
        }/$version/profile/json"

    override fun getAddonVersion(): String = this.version

    override fun isVersion(versionString: String): Boolean {
        return this.version == versionString
    }
}
