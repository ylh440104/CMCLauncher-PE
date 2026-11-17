package com.cmc.launcher.game.renderer.config

data class ResolvedConfigEntry(
    val entry: ConfigEntry,
    val value: ConfigScalar,
    val enabled: Boolean?,
)

data class ResolvedRendererConfig(
    val fixedEnv: Map<String, String>,
    val entries: List<ResolvedConfigEntry>
) {

    val environment: Map<String, String> = buildMap {
        putAll(fixedEnv)
        this@ResolvedRendererConfig.entries.forEach { resolved ->
            val target = resolved.entry.target as? ConfigTarget.Environment ?: return@forEach
            when (val entry = resolved.entry) {
                is SwitchEntry -> {
                    if ((resolved.value as? ConfigScalar.BooleanValue)?.value == true) {
                        put(target.key, entry.enabledValue.asString())
                    }
                }
                is SingleChoiceEntry -> {
                    if (resolved.enabled != false) put(target.key, resolved.value.asString())
                }
                is OrderedChoiceEntry -> {
                    if (resolved.enabled != false) put(target.key, resolved.value.asString())
                }
                is NumberEntry -> effectiveNumberValue(entry, resolved.value)?.let {
                    put(target.key, it.asString())
                }
                is TextEntry -> {
                    val value = resolved.value.asString()
                    if (value.isNotEmpty()) put(target.key, value)
                }
            }
        }
    }

    val sfpewLayerEnabled: Boolean? = entries
        .firstOrNull { it.entry.target is ConfigTarget.SfpewLayer }
        ?.let { (it.value as? ConfigScalar.BooleanValue)?.value }

    val jsonFields: Map<String, ConfigScalar> = buildMap {
        this@ResolvedRendererConfig.entries.forEach { resolved ->
            val target = resolved.entry.target as? ConfigTarget.JsonField ?: return@forEach
            when (val entry = resolved.entry) {
                is SwitchEntry -> {
                    val enabled = (resolved.value as? ConfigScalar.BooleanValue)?.value == true
                    put(target.key, ConfigScalar.IntValue(if (enabled) 1 else 0))
                }
                is SingleChoiceEntry -> {
                    if (resolved.enabled != false) put(target.key, resolved.value)
                }
                is OrderedChoiceEntry -> {
                    if (resolved.enabled != false) put(target.key, resolved.value)
                }
                is NumberEntry -> {
                    effectiveNumberValue(entry, resolved.value)?.let { put(target.key, it) }
                }
                is TextEntry -> {
                    put(target.key, resolved.value)
                }
            }
        }
    }
}

data class ResolvedEntryState(
    val value: ConfigScalar,
    val enabled: Boolean?,
)

fun resolveEntry(
    rendererId: String,
    entry: ConfigEntry,
    localStore: RendererConfigStore?,
    globalStore: RendererConfigStore? = null,
    inheritedValues: Map<String, ConfigScalar> = emptyMap()
): ResolvedEntryState {
    val localValue = localStore?.getValue(rendererId, entry.id)
    val globalValue = globalStore?.takeIf { it !== localStore }?.getValue(rendererId, entry.id)
    val localEnabled = localStore?.getEnabled(rendererId, entry.id)
    val globalEnabled = globalStore?.takeIf { it !== localStore }?.getEnabled(rendererId, entry.id)
    val enabled = if (entry.defaultEnabled != null) {
        localEnabled ?: globalEnabled ?: entry.defaultEnabled
    } else null
    val orderedEntry = entry as? OrderedChoiceEntry
    val defaultValue = orderedEntry?.defaultValue(inheritedValues[orderedEntry.inheritFrom])
        ?: entry.defaultValue
    return ResolvedEntryState(
        value = entry.coerce(localValue ?: globalValue ?: defaultValue),
        enabled = enabled,
    )
}

fun resolveRendererConfig(
    rendererId: String,
    spec: RendererConfigSpec,
    versionStore: VersionRendererConfigStore? = null,
    globalStore: RendererConfigStore = GlobalRendererConfigStore
): ResolvedRendererConfig {
    val inheritedValues = mutableMapOf<String, ConfigScalar>()
    return ResolvedRendererConfig(
        fixedEnv = spec.fixedEnv,
        entries = spec.entries.map { entry ->
            val state = resolveEntry(rendererId, entry, versionStore, globalStore, inheritedValues)
            inheritedValues[entry.id] = state.value
            ResolvedConfigEntry(
                entry = entry,
                value = state.value,
                enabled = state.enabled,
            )
        }
    )
}

private fun effectiveNumberValue(entry: NumberEntry, value: ConfigScalar): ConfigScalar? =
    if ((value as? ConfigScalar.StringValue)?.value?.isEmpty() == true) entry.emptyValue else value
