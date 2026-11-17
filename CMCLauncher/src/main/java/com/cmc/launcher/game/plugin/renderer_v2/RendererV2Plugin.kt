package com.cmc.launcher.game.plugin.renderer_v2

import com.cmc.launcher.game.plugin.Plugin
import com.cmc.launcher.game.plugin.renderer_v2.data.RendererConfig
import com.cmc.launcher.game.renderer.ConfigurableRenderer

class RendererV2Data(
    val packageName: String,
    val nativePath: String,
    val summary: String,
    val renderer: RendererConfig,
    genSummary: (metaString: String) -> String?,
): ConfigurableRenderer, Plugin {
    override val configSpec = renderer.toConfigSpec(genSummary)

    override val mesaBased: Boolean = true

    override fun getIdentifier(): String = packageName
    override fun getNativeLibPath(): String = nativePath

    override fun getRendererId(): String = renderer.rendererId
    override fun getUniqueIdentifier(): String = packageName
    override fun getRendererName(): String = renderer.displayName
    override fun getRendererSummary(): String = summary
    override fun getMinMCVersion(): String? = renderer.minMCVer
    override fun getMaxMCVersion(): String? = renderer.maxMCVer
    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { renderer.dlopenLibPaths }
    override fun getRendererLibrary(): String = renderer.rendererGLPath
    override fun getRendererEGL(): String = renderer.rendererEGLPath
}
