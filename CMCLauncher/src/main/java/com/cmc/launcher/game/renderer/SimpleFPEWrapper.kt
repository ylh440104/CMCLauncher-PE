package com.cmc.launcher.game.renderer

import com.cmc.launcher.game.plugin.Plugin
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.mod.LocalMod
import com.cmc.launcher.game.version.mod.isEnabled
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.utils.logging.Logger

private const val TAG = "SimpleFPEWrapper"

fun getSfpewLibraryPath(): String = "${PathManager.DIR_NATIVE_LIB}/libSimpleFPEWrapper.so"

fun isSfpewConfiguredFor(renderer: RendererInterface, version: Version): Boolean {
    return if (renderer is Plugin) {
        version.isSfpewPluginLayerEnabled()
    } else {
        renderer.supportsSfpewByDefault()
    }
}

fun isSfpewEnabledFor(
    renderer: RendererInterface,
    version: Version,
    launchConfig: RendererLaunchConfig? = null
): Boolean =
    !version.fpeEmulatorModDetected &&
        (launchConfig?.sfpewLayerEnabled ?: isSfpewConfiguredFor(renderer, version))

fun detectFpeEmulatorMods(mods: List<LocalMod>): Boolean {
    val fpeEmulatorModIds = setOf("angelica", "actinium")

    val detectedIds = mods.filter {
        it.file.isEnabled() && it.id in fpeEmulatorModIds
    }.mapTo(mutableSetOf()) { it.id }

    if (detectedIds.isNotEmpty()) {
        Logger.info(
            TAG,
            "FPE emulator mod detected (${detectedIds.joinToString()}), compatibility layer disabled"
        )
    }
    return detectedIds.isNotEmpty()
}
