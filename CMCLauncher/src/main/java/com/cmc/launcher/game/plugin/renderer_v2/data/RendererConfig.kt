package com.cmc.launcher.game.plugin.renderer_v2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class RendererConfig(
    @SerialName("displayName")
    val displayName: String,
    @SerialName("rendererId")
    val rendererId: String,
    @SerialName("rendererGLPath")
    val rendererGLPath: String,
    @SerialName("rendererEGLPath")
    val rendererEGLPath: String,
    @SerialName("dlopenLibPaths")
    val dlopenLibPaths: List<String>,
    @SerialName("env")
    val env: List<Env>,
    @SerialName("minMCVer")
    val minMCVer: String?,
    @SerialName("maxMCVer")
    val maxMCVer: String?,
) {
    @Serializable
    sealed interface Env {

        @Serializable
        @SerialName("NormalEnv")
        data class NormalEnv(
            @SerialName("key")
            val key: String,
            @SerialName("value")
            val value: String,
        ): Env

        @Serializable
        @SerialName("SelectableEnv")
        data class SelectableEnv(
            @SerialName("key")
            val key: String,
            @SerialName("title")
            val title: MetaString? = null,
            @SerialName("check")
            val check: Boolean? = true,
            @SerialName("items")
            val items: EnvItems
        ): Env

        @Serializable
        @SerialName("CustomizableEnv")
        data class CustomizableEnv(
            @SerialName("key")
            val key: String,
            @SerialName("title")
            val title: MetaString? = null,
            @SerialName("defaultValue")
            val defaultValue: String? = null,
        ): Env

        @Serializable
        @SerialName("ToggleableEnv")
        data class ToggleableEnv(
            @SerialName("key")
            val key: String,
            @SerialName("value")
            val value: String,
            @SerialName("title")
            val title: MetaString? = null,
            @SerialName("toggle")
            val toggle: Boolean = true,
        ): Env
    }

    @Serializable
    data class EnvItems(
        @SerialName("defaultValue")
        val defaultValue: String,
        @SerialName("values")
        val values: List<String>,
    )

    @Serializable
    data class MetaString(
        @SerialName("key")
        val key: String
    )
}

private fun String.resolveNativePath(nativeLibDir: String): String {
    if (!startsWith("**|")) return this
    return File(nativeLibDir, removePrefix("**|")).absolutePath
}

fun RendererConfig.resolveNativePaths(nativeLibDir: String): RendererConfig {
    fun String.replacePath() = this.resolveNativePath(nativeLibDir)

    return copy(
        rendererGLPath = rendererGLPath.replacePath(),
        rendererEGLPath = rendererEGLPath.replacePath(),
        dlopenLibPaths = dlopenLibPaths.map { it.replacePath() },
        env = env.map { env ->
            when (env) {
                is RendererConfig.Env.NormalEnv -> env.copy(value = env.value.replacePath())
                is RendererConfig.Env.ToggleableEnv -> env.copy(value = env.value.replacePath())

                else -> env
            }
        }
    )
}
