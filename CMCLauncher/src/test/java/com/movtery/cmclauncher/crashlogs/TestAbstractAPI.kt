package com.movtery.cmclauncher.crashlogs

import com.movtery.cmclauncher.crashlogs.platform.MCLogsAPI
import com.movtery.cmclauncher.crashlogs.platform.MirroredAPI
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TestAbstractAPI {
    @Test
    fun testMirroredAPI() {
        runBlocking {
            MirroredAPI.onUpload("TEST CONTENT")
        }
    }

    @Test
    fun testMCLogsAPI() {
        runBlocking {
            MCLogsAPI.onUpload("TEST CONTENT")
        }
    }
}
