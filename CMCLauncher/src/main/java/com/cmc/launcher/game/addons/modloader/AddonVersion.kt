package com.cmc.launcher.game.addons.modloader

abstract class AddonVersion(

    open val inherit: String
) {

    abstract fun getAddonVersion(): String

    abstract fun isVersion(versionString: String): Boolean
}
