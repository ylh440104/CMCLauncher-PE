package com.cmc.launcher.game.addons.modloader.fabriclike.quilt

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.fabriclike.FabricLikeVersion

class QuiltVersion(
    inherit: String,
    version: String
) : FabricLikeVersion(
    inherit = inherit,
    loaderName = ModLoader.QUILT.displayName,
    version = version,

    stable = version.contains("beta")
) {
    override val loaderUrl: String
        get() = "${QuiltVersions.officialUrl}/versions/loader"
}
