package com.movtery.cmclauncher.game.addons.mirror

import junit.framework.TestCase.assertEquals
import org.junit.Test

class NeoForgeCDNUrlTest {
    private val installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.215/neoforge-21.1.215-installer.jar"
    private val cdnInstallerUrl = "https://neoforged.forgecdn.net/releases/net/neoforged/neoforge/21.1.215/neoforge-21.1.215-installer.jar"

    @Test
    fun testMirrorFirstCandidates() {
        assertEquals(
            listOf(
                "https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/21.1.215/neoforge-21.1.215-installer.jar",
                installerUrl,
                cdnInstallerUrl
            ),
            installerUrl.mapBMCLMirrorUrls(mainland = true, priority = MirrorPriority.MIRROR_FIRST)
        )
    }

    @Test
    fun testOfficialFirstCandidates() {
        assertEquals(
            listOf(installerUrl, cdnInstallerUrl),
            installerUrl.mapBMCLMirrorUrls(mainland = true, priority = MirrorPriority.OFFICIAL)
        )
    }

    @Test
    fun testMetadataFallbackOutsideMainland() {
        val metadataUrl = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
        assertEquals(
            listOf(
                metadataUrl,
                "https://neoforged.forgecdn.net/api/maven/versions/releases/net/neoforged/neoforge"
            ),
            metadataUrl.mapBMCLMirrorUrls(mainland = false, priority = MirrorPriority.OFFICIAL)
        )
    }

    @Test
    fun testUnrelatedUrlOutsideMainland() {
        val url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        assertEquals(
            listOf(url),
            url.mapBMCLMirrorUrls(mainland = false, priority = MirrorPriority.OFFICIAL)
        )
    }
}
