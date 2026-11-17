package com.cmc.launcher.game.renderer

import com.cmc.launcher.game.renderer.renderers.FreedrenoRenderer
import com.cmc.launcher.game.renderer.renderers.GL4ESRenderer
import com.cmc.launcher.game.renderer.renderers.KopperZinkRenderer
import com.cmc.launcher.game.renderer.renderers.MobileGluesRenderer
import com.cmc.launcher.game.renderer.renderers.NGGL4ESRenderer
import com.cmc.launcher.game.renderer.renderers.PanfrostRenderer
import com.cmc.launcher.game.renderer.renderers.VirGLRenderer
import com.cmc.launcher.utils.logging.Logger

private const val TAG = "Renderers"

object Renderers {
    private val renderers: MutableList<RendererInterface> = mutableListOf()
    private var currentRenderer: RendererInterface? = null
    private var isInitialized: Boolean = false

    fun init(
        reset: Boolean = false
    ) {
        if (isInitialized && !reset) return
        isInitialized = true

        if (reset) {
            renderers.clear()
            currentRenderer = null
        }

        addRenderers(
            NGGL4ESRenderer,
            GL4ESRenderer,
            KopperZinkRenderer,
            MobileGluesRenderer,
            VirGLRenderer,
            FreedrenoRenderer,
            PanfrostRenderer
        )
    }

    fun getRenderers(): List<RendererInterface> = renderers

    fun getLegacyRendererIds(): Map<String, String> = buildMap {
        renderers.filterIsInstance<ConfigurableRenderer>().forEach { renderer ->
            renderer.legacyConfigKeys.forEach { put(it, renderer.getUniqueIdentifier()) }
        }
    }

    fun addRenderers(vararg renderers: RendererInterface) {
        renderers.forEach { renderer ->
            addRenderer(renderer)
        }
    }

    fun addRenderer(renderer: RendererInterface): Boolean {
        return if (renderers.any { it.getUniqueIdentifier() == renderer.getUniqueIdentifier() }) {
            Logger.warning(TAG, "The unique identifier of this renderer (${renderer.getRendererName()} - ${renderer.getUniqueIdentifier()}) conflicts with an already loaded renderer. " +
                    "Normally, this shouldn't happen. You deliberately caused this conflict, didn't you, user?")
            false
        } else {
            renderers.add(renderer)
            Logger.info(TAG, "Renderer loaded: ${renderer.getRendererName()} (${renderer.getRendererId()} - ${renderer.getUniqueIdentifier()})")
            true
        }
    }

    fun setCurrentRenderer(uniqueIdentifier: String, retryToFirstOnFailure: Boolean = true) {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        currentRenderer = renderers.find { it.getUniqueIdentifier() == uniqueIdentifier } ?: run {
            if (retryToFirstOnFailure) {
                val renderer = renderers[0]
                Logger.warning(TAG, "Incompatible renderer $uniqueIdentifier will be replaced with ${renderer.getUniqueIdentifier()} (${renderer.getRendererName()})")
                renderer
            } else null
        }
    }

    fun getCurrentRenderer(): RendererInterface {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        return currentRenderer ?: throw IllegalStateException("Current renderer not set")
    }

    fun isCurrentRendererValid(): Boolean = isInitialized && currentRenderer != null
}
