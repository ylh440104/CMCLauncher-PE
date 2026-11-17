package com.movtery.cmclauncher.game.version.installed

import com.movtery.cmclauncher.utils.GSON
import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.io.File

class VersionConfigTest {
    @Test
    fun testSfpewSettingsSurviveCopyAndSerialization() {
        val config = VersionConfig(
            filePath = File("test"),
            sfpewPluginLayer = SettingState.ENABLE,
            sfpewRelaxedContext = SettingState.DISABLE
        )

        val copy = config.copy()
        assertEquals(SettingState.ENABLE, copy.sfpewPluginLayer)
        assertEquals(SettingState.DISABLE, copy.sfpewRelaxedContext)

        val restored = GSON.fromJson(GSON.toJson(config), VersionConfig::class.java)
        assertEquals(SettingState.ENABLE, restored.sfpewPluginLayer)
        assertEquals(SettingState.DISABLE, restored.sfpewRelaxedContext)
    }

    @Test
    fun testLegacyConfigFollowsGlobalSfpewSettings() {
        val config = GSON.fromJson("{}", VersionConfig::class.java)

        assertEquals(SettingState.FOLLOW_GLOBAL, config.sfpewPluginLayer)
        assertEquals(SettingState.FOLLOW_GLOBAL, config.sfpewRelaxedContext)
    }
}
