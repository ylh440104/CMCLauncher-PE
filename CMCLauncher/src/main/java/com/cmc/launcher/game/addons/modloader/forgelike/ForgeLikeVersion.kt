package com.cmc.launcher.game.addons.modloader.forgelike

import com.cmc.launcher.game.addons.modloader.AddonVersion

abstract class ForgeLikeVersion(

    val loaderName: String,

    val forgeBuildVersion: ForgeBuildVersion,

    val versionName: String,

    inherit: String,

    val fileExtension: String
) : AddonVersion(
    inherit = inherit
) {

    val isLegacy: Boolean get() = forgeBuildVersion.major < 20

    override fun getAddonVersion(): String = forgeBuildVersion.toString()

    override fun isVersion(versionString: String): Boolean {
        val target = ForgeBuildVersion.parse(versionString).toString()
        return target == forgeBuildVersion.toString()
    }
}
