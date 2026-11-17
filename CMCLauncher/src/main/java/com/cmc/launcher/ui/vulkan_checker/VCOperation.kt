package com.cmc.launcher.ui.vulkan_checker

import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.utils.device.VulkanCapabilities

sealed interface VCOperation {
    data object None: VCOperation

    data class Tip(val version: Version): VCOperation

    data class Result(
        val data: VulkanCapabilities?,
        val useTurnip: Boolean = false
    ): VCOperation
}
