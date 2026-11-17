package com.movtery.cmclauncher.game.download.jvm_server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmExclusiveProcessTest {

    private val main = "com.movtery.cmclauncher.v2.debug"

    @Test
    fun `jvm and game processes are exclusive`() {
        assertTrue(isJvmExclusiveProcess("$main:jvm", main))
        assertTrue(isJvmExclusiveProcess("$main:game", main))
    }

    @Test
    fun `filemanager and other sub processes are not exclusive`() {
        assertFalse(isJvmExclusiveProcess("$main:filemanager", main))
        assertFalse(isJvmExclusiveProcess(main, main))
        assertFalse(isJvmExclusiveProcess("$main:other", main))
    }

    @Test
    fun `foreign packages never match`() {

        assertFalse(isJvmExclusiveProcess("com.other.app:jvm", main))
        assertFalse(isJvmExclusiveProcess("${main}evil:jvm", main))
    }
}
