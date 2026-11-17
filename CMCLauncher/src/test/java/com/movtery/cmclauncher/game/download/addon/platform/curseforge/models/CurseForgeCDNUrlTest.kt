package com.movtery.cmclauncher.game.download.addon.platform.curseforge.models

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.net.URLDecoder

class CurseForgeCDNUrlTest {

    @Test
    fun testEncodeSpaces() {
        assertEquals(
            "https://edge.forgecdn.net/files/9097/219/Mechanical%20Horizons%202026.1.0.zip",
            curseForgeCDNUrl(fileId = 9097219, fileName = "Mechanical Horizons 2026.1.0.zip")
        )
    }

    @Test
    fun testSpecialCharactersRoundTrip() {
        val fileName = "模组 v1.0 + 补丁 (50%off) [测试].jar"
        val url = curseForgeCDNUrl(fileId = 1, fileName = fileName)
        assertEquals(-1, url.indexOf(' '))
        assertEquals(
            fileName,
            URLDecoder.decode(url.substringAfterLast('/'), "UTF-8")
        )
    }
}
