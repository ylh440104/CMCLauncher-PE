package com.movtery.cmclauncher.game.renderer.config

import com.movtery.cmclauncher.game.renderer.renderers.MobileGluesRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedChoiceEntryTest {
    private val globalEntry = orderedEntry(
        "native", "multiindirect", "multibasevertex", "multiarrays",
        "indirect", "basevertex", "unroll", "compute"
    )

    @Test
    fun keepsDefaultOrder() {
        assertEquals(globalEntry.defaultValue, globalEntry.coerce(globalEntry.defaultValue))
    }

    @Test
    fun removesDuplicatesAndUnknownValuesAndAppendsMissingValues() {
        val value = globalEntry.coerce(
            ConfigScalar.StringValue("compute,native,unknown,compute,multiarrays")
        )

        assertEquals(
            "compute,native,multiarrays,multiindirect,multibasevertex,indirect,basevertex,unroll",
            value.asString()
        )
    }

    @Test
    fun functionEntryRejectsBackendsItDoesNotImplement() {
        val entry = orderedEntry("multiarrays", "multiindirect", "unroll")

        val value = entry.coerce(
            ConfigScalar.StringValue("compute,multiindirect,basevertex")
        )

        assertEquals("multiindirect,multiarrays,unroll", value.asString())
    }

    @Test
    fun enablingEntryWithoutEditingValueStillWritesJson() {
        val store = MemoryRendererConfigStore()
        val entry = globalEntry.copy(defaultEnabled = false)
        store.putEnabled("renderer", entry.id, true)

        val config = resolveRendererConfig(
            rendererId = "renderer",
            spec = RendererConfigSpec(listOf(entry)),
            globalStore = store
        )

        assertTrue(config.jsonFields.containsKey(entry.id))
        assertEquals(entry.defaultValue, config.jsonFields[entry.id])
    }

    @Test
    fun disabledEntryDoesNotWriteJson() {
        val store = MemoryRendererConfigStore()
        val entry = globalEntry.copy(defaultEnabled = false)
        store.putEnabled("renderer", entry.id, false)

        val config = resolveRendererConfig(
            rendererId = "renderer",
            spec = RendererConfigSpec(listOf(entry)),
            globalStore = store
        )

        assertFalse(config.jsonFields.containsKey(entry.id))
    }

    @Test
    fun mobileGluesDefaultsAreFullyWrittenToJson() {
        val store = MemoryRendererConfigStore()

        val config = resolveRendererConfig(
            rendererId = "renderer",
            spec = MobileGluesRenderer.configSpec,
            globalStore = store
        )

        assertEquals(ConfigScalar.IntValue(0), config.jsonFields["enableANGLE"])
        assertEquals(ConfigScalar.IntValue(1), config.jsonFields["enableExtTimerQuery"])
        assertEquals(ConfigScalar.IntValue(32), config.jsonFields["maxGlslCacheSize"])
    }

    @Test
    fun mobileGluesSfpewLayerSwitchIsStoredButNotExported() {
        val store = MemoryRendererConfigStore()

        val default = resolveRendererConfig(
            rendererId = "renderer",
            spec = MobileGluesRenderer.configSpec,
            globalStore = store
        )
        assertEquals(true, default.sfpewLayerEnabled)

        assertFalse(default.jsonFields.containsKey("sfpewLayer"))
        assertFalse(default.environment.containsKey("sfpewLayer"))

        store.putValue("renderer", "sfpewLayer", ConfigScalar.BooleanValue(false))
        val disabled = resolveRendererConfig(
            rendererId = "renderer",
            spec = MobileGluesRenderer.configSpec,
            globalStore = store
        )
        assertEquals(false, disabled.sfpewLayerEnabled)
    }

    @Test
    fun mobileGluesUsesOfficialBackendSetsAndDefaultOrders() {
        val expected = mapOf(
            "multidrawOrder" to listOf(
                "native", "multiindirect", "multibasevertex", "multiarrays",
                "indirect", "basevertex", "unroll", "compute"
            ),
            "multidrawOrderArrays" to listOf("multiarrays", "multiindirect", "unroll"),
            "multidrawOrderElements" to listOf(
                "multiarrays", "multiindirect", "multibasevertex", "indirect", "unroll"
            ),
            "multidrawOrderElementsBaseVertex" to listOf(
                "multibasevertex", "multiindirect", "indirect", "basevertex", "unroll", "compute"
            ),
            "multidrawOrderArraysIndirect" to listOf("multiindirect", "indirect"),
            "multidrawOrderElementsIndirect" to listOf("multiindirect", "indirect")
        )
        val entries = MobileGluesRenderer.configSpec.entries.filterIsInstance<OrderedChoiceEntry>()
            .associateBy { it.id }

        assertEquals(expected.keys, entries.keys)
        expected.forEach { (id, order) ->
            val entry = entries.getValue(id)
            assertEquals(order.joinToString(","), entry.defaultValue.value)
            assertEquals(order.toSet(), entry.options.mapTo(mutableSetOf()) { it.value.asString() })
        }
    }

    @Test
    fun enabledExceptionsExpandCustomGlobalOrder() {
        val store = MemoryRendererConfigStore()
        store.putValue(
            "renderer",
            "multidrawOrder",
            ConfigScalar.StringValue(
                "compute,native,indirect,multiindirect,multibasevertex,multiarrays,basevertex,unroll"
            )
        )
        val expected = mapOf(
            "multidrawOrderArrays" to "multiarrays,multiindirect,unroll",
            "multidrawOrderElements" to "multiarrays,indirect,multiindirect,multibasevertex,unroll",
            "multidrawOrderElementsBaseVertex" to
                    "compute,multibasevertex,indirect,multiindirect,basevertex,unroll",
            "multidrawOrderArraysIndirect" to "multiindirect,indirect",
            "multidrawOrderElementsIndirect" to "multiindirect,indirect"
        )
        expected.keys.forEach { store.putEnabled("renderer", it, true) }

        val config = resolveRendererConfig(
            rendererId = "renderer",
            spec = MobileGluesRenderer.configSpec,
            globalStore = store
        )

        expected.forEach { (id, order) ->
            assertEquals(ConfigScalar.StringValue(order), config.jsonFields[id])
        }
    }

    @Test
    fun enablingExceptionSavesCurrentExpandedOrder() {
        val store = MemoryRendererConfigStore()
        store.putValue(
            "renderer",
            "multidrawOrder",
            ConfigScalar.StringValue(
                "compute,native,indirect,multiindirect,multibasevertex,multiarrays,basevertex,unroll"
            )
        )
        val session = RendererConfigSession("renderer", MobileGluesRenderer.configSpec, store, store)
        val global = session.states.single { it.entry.id == "multidrawOrder" }
        val exception = session.states.single { it.entry.id == "multidrawOrderElementsBaseVertex" }
        val expected = ConfigScalar.StringValue(
            "compute,multibasevertex,indirect,multiindirect,basevertex,unroll"
        )

        assertEquals(expected, exception.value)
        exception.saveEnabled(true)
        assertEquals(expected, store.getValue("renderer", exception.entry.id))

        global.saveValue(ConfigScalar.StringValue("unroll,native,compute"))
        exception.reload()
        assertEquals(expected, exception.value)
    }

    private fun orderedEntry(vararg values: String) = OrderedChoiceEntry(
        id = "multidrawOrder",
        title = null,
        description = null,
        options = values.map { ChoiceOption(ConfigScalar.StringValue(it)) },
        defaultValue = ConfigScalar.StringValue(values.joinToString(",")),
        target = ConfigTarget.JsonField("multidrawOrder")
    )

    private class MemoryRendererConfigStore : RendererConfigStore {
        private val values = mutableMapOf<Pair<String, String>, ConfigScalar>()
        private val enabled = mutableMapOf<Pair<String, String>, Boolean>()

        override fun getValue(rendererId: String, entryId: String): ConfigScalar? =
            values[rendererId to entryId]

        override fun putValue(rendererId: String, entryId: String, value: ConfigScalar) {
            values[rendererId to entryId] = value
        }

        override fun getEnabled(rendererId: String, entryId: String): Boolean? =
            enabled[rendererId to entryId]

        override fun putEnabled(rendererId: String, entryId: String, enabled: Boolean) {
            this.enabled[rendererId to entryId] = enabled
        }

        override fun removeEntry(rendererId: String, entryId: String) {
            values.remove(rendererId to entryId)
            enabled.remove(rendererId to entryId)
        }

        override fun removeRenderer(rendererId: String) {
            values.keys.removeAll { it.first == rendererId }
            enabled.keys.removeAll { it.first == rendererId }
        }

        override fun keys(rendererId: String): Set<String> =
            (values.keys + enabled.keys).filter { it.first == rendererId }.mapTo(mutableSetOf()) { it.second }
    }
}
