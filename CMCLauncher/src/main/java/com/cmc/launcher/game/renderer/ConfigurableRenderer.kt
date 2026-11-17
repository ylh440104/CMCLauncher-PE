package com.cmc.launcher.game.renderer

import androidx.annotation.WorkerThread
import com.cmc.launcher.game.renderer.config.GlobalRendererConfigStore
import com.cmc.launcher.game.renderer.config.RendererConfigSpec
import com.cmc.launcher.game.renderer.config.ResolvedRendererConfig
import com.cmc.launcher.game.renderer.config.VersionRendererConfigStore
import com.cmc.launcher.game.renderer.config.removeStaleEntries
import com.cmc.launcher.game.renderer.config.resolveRendererConfig
import com.cmc.launcher.game.version.installed.Version

interface ConfigurableRenderer : RendererInterface {
    val configSpec: RendererConfigSpec

    val legacyConfigKeys: List<String> get() = listOf(getUniqueIdentifier())

    @WorkerThread
    fun getConfiguredEnv(config: ResolvedRendererConfig): Map<String, String> = config.environment

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        getConfiguredEnv(resolveRendererConfig(getUniqueIdentifier(), configSpec))
    }
}

data class RendererLaunchConfig(

    val environment: Map<String, String>,

    val sfpewLayerEnabled: Boolean?
)

@WorkerThread
fun prepareRendererLaunchConfig(renderer: ConfigurableRenderer, version: Version): RendererLaunchConfig {
    val rendererId = renderer.getUniqueIdentifier()
    GlobalRendererConfigStore.migrateLegacy(rendererId, renderer.legacyConfigKeys)
    GlobalRendererConfigStore.removeStaleEntries(rendererId, renderer.configSpec)
    val versionStore = if (version.getVersionConfig().renderer == rendererId) {
        VersionRendererConfigStore(version.getVersionPath(), Renderers.getLegacyRendererIds())
            .apply { removeStaleEntries(rendererId, renderer.configSpec) }
    } else null
    val config = resolveRendererConfig(rendererId, renderer.configSpec, versionStore)
    return RendererLaunchConfig(
        environment = renderer.getConfiguredEnv(config),
        sfpewLayerEnabled = config.sfpewLayerEnabled
    )
}
