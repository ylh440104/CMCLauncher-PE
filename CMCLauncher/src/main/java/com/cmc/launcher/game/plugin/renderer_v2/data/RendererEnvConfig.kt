package com.cmc.launcher.game.plugin.renderer_v2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RendererEnvConfig(

    @SerialName("renderers")
    val renderers: MutableMap<String, RendererEnvs> = mutableMapOf()
) {
    companion object {
        const val CONFIG_FILE_NAME = "renderer.config"
    }
}

@Serializable
data class RendererEnvs(

    @SerialName("envs")
    val envs: MutableMap<String, String> = mutableMapOf(),

    @SerialName("checks")
    val checks: MutableMap<String, Boolean> = mutableMapOf()
)
