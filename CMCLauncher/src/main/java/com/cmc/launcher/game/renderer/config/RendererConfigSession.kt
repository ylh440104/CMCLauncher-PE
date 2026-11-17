package com.cmc.launcher.game.renderer.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class RendererConfigSession(
    private val rendererId: String,
    spec: RendererConfigSpec,
    private val localStore: RendererConfigStore,
    private val globalStore: RendererConfigStore = GlobalRendererConfigStore
) {
    init {
        localStore.removeStaleEntries(rendererId, spec)
        if (globalStore !== localStore) globalStore.removeStaleEntries(rendererId, spec)
    }

    val states: List<ConfigEntryState> = mutableListOf<ConfigEntryState>().also { states ->
        spec.entries.forEach { entry ->
            states += ConfigEntryState(rendererId, entry, localStore, globalStore) {
                states.associate { it.entry.id to it.value }
            }
        }
    }

    val hasOverrides: Boolean get() = states.any { it.isOverridden }

    fun resetAll() {
        localStore.removeRenderer(rendererId)
        states.forEach { it.reload() }
    }
}

class ConfigEntryState(
    private val rendererId: String,
    val entry: ConfigEntry,
    private val localStore: RendererConfigStore,
    private val globalStore: RendererConfigStore,
    private val inheritedValues: () -> Map<String, ConfigScalar> = { emptyMap() }
) {
    var value by mutableStateOf(entry.defaultValue)
        private set
    var enabled by mutableStateOf(entry.defaultEnabled)
        private set
    var isOverridden by mutableStateOf(false)
        private set

    init {
        reload()
    }

    fun saveValue(newValue: ConfigScalar) {
        val coerced = entry.coerce(newValue)
        localStore.putValue(rendererId, entry.id, coerced)
        value = coerced
        updateOverride()
    }

    fun saveEnabled(newValue: Boolean) {
        if (newValue && entry is OrderedChoiceEntry) {
            reload()
            localStore.putValue(rendererId, entry.id, value)
        }
        localStore.putEnabled(rendererId, entry.id, newValue)
        enabled = newValue
        updateOverride()
    }

    fun reset() {
        localStore.removeEntry(rendererId, entry.id)
        reload()
    }

    fun reload() {
        val state = resolveEntry(rendererId, entry, localStore, globalStore, inheritedValues())
        value = state.value
        enabled = state.enabled
        updateOverride()
    }

    private fun updateOverride() {
        isOverridden = localStore.getValue(rendererId, entry.id) != null ||
                localStore.getEnabled(rendererId, entry.id) != null
    }
}
