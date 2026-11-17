package com.movtery.cmclauncher.keepalive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TaskKeepAliveTest {

    @Before
    fun setUp() = TaskKeepAlive.reset()

    @After
    fun tearDown() = TaskKeepAlive.reset()

    @Test
    fun `acquire and release keep the count balanced`() {
        assertEquals(0, TaskKeepAlive.count)

        TaskKeepAlive.acquire()
        assertEquals(1, TaskKeepAlive.count)
        TaskKeepAlive.acquire()
        assertEquals(2, TaskKeepAlive.count)

        TaskKeepAlive.release()
        assertEquals(1, TaskKeepAlive.count)
        TaskKeepAlive.release()
        assertEquals(0, TaskKeepAlive.count)
    }

    @Test
    fun `extra release never underflows`() {
        TaskKeepAlive.release()
        assertEquals(0, TaskKeepAlive.count)

        TaskKeepAlive.acquire()
        TaskKeepAlive.release()

        TaskKeepAlive.release()
        assertEquals(0, TaskKeepAlive.count)
    }

    @Test
    fun `reset clears every acquired reference`() {
        repeat(5) { TaskKeepAlive.acquire() }
        assertEquals(5, TaskKeepAlive.count)

        TaskKeepAlive.reset()
        assertEquals(0, TaskKeepAlive.count)
    }

    @Test
    fun `concurrent acquire and release conserve the count`() = runBlocking<Unit> {
        val workers = 32

        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.Default) {
                    TaskKeepAlive.acquire()
                }
            }
        }
        assertEquals(workers, TaskKeepAlive.count)

        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.Default) {
                    TaskKeepAlive.release()
                }
            }
        }
        assertEquals(0, TaskKeepAlive.count)
    }
}
