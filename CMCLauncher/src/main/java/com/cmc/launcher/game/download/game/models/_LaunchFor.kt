package com.cmc.launcher.game.download.game.models

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.fabriclike.FabricLikeVersion
import com.cmc.launcher.game.addons.modloader.forgelike.ForgeLikeVersion
import com.cmc.launcher.game.addons.modloader.optifine.OptiFineVersion

fun OptiFineVersion.toLaunchForInfo(): LaunchFor.Info {
    return LaunchFor.Info(
        version = this.version,
        name = ModLoader.OPTIFINE.displayName
    )
}

fun ForgeLikeVersion.toLaunchForInfo(): LaunchFor.Info {
    return LaunchFor.Info(
        version = this.versionName,
        name = this.loaderName
    )
}

fun FabricLikeVersion.toLaunchForInfo(): LaunchFor.Info {
    return LaunchFor.Info(
        version = this.version,
        name = this.loaderName
    )
}
