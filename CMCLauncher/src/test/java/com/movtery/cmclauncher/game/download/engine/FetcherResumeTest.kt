package com.movtery.cmclauncher.game.download.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Random
import java.util.concurrent.TimeUnit

class FetcherResumeTest {

    private val servers = mutableListOf<MockWebServer>()
    private var workDir: File? = null

    private val content = ByteArray(512 * 1024).also { Random(42).nextBytes(it) }
    private val etag = "\"etag-42\""

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        workDir?.takeIf { it.exists() }?.deleteRecursively()
    }

    private fun startServer(dispatcher: Dispatcher): MockWebServer =
        MockWebServer().also { server ->
            server.dispatcher = dispatcher
            server.start()
            servers.add(server)
        }

    private fun newWorkDir(): File = Files.createTempDirectory("resume-test").toFile().also { workDir = it }

    private fun completeResponse(validatorHeaders: List<Pair<String, String>>, code: Int = 200): MockResponse {
        val builder = MockResponse.Builder()
            .code(code)
            .addHeader("accept-ranges", "bytes")
        if (code == 206) {

            builder.addHeader("Content-Range", "bytes 0-${content.size - 1}/${content.size}")
        }
        validatorHeaders.forEach { (name, value) -> builder.addHeader(name, value) }
        builder.body(Buffer().write(content))
        return builder.build()
    }

    private fun interruptedResponse(validatorHeaders: List<Pair<String, String>>): MockResponse {
        val builder = MockResponse.Builder()
            .code(200)
            .addHeader("accept-ranges", "bytes")
            .onResponseBody(SocketEffect.CloseSocket())
            .throttleBody(8192, 1, TimeUnit.SECONDS)
        validatorHeaders.forEach { (name, value) -> builder.addHeader(name, value) }
        builder.body(Buffer().write(content))
        return builder.build()
    }

    private fun resumedSlice(request: RecordedRequest): MockResponse {
        val start = RANGE_PATTERN.find(request.headers["Range"]!!)!!.groupValues[1].toLong()
        return MockResponse.Builder()
            .code(206)
            .addHeader("accept-ranges", "bytes")
            .addHeader("Content-Range", "bytes $start-${content.size - 1}/${content.size}")
            .body(Buffer().write(content, start.toInt(), content.size - start.toInt()))
            .build()
    }

    private fun handler(
        validatorHeaders: List<Pair<String, String>>,
        onRangeRequest: (request: RecordedRequest) -> MockResponse
    ): Pair<Dispatcher, MutableList<RecordedRequest>> {
        val requests = mutableListOf<RecordedRequest>()
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val range = request.headers["Range"]
                return when {
                    range != null -> onRangeRequest(request)
                    requests.size == 1 -> interruptedResponse(validatorHeaders)
                    else -> completeResponse(validatorHeaders)
                }
            }
        }
        return dispatcher to requests
    }

    private val RecordedRequest.rangeStart: Long?
        get() = headers["Range"]?.let { RANGE_PATTERN.find(it)?.groupValues?.get(1)?.toLong() }

    @Test
    fun `retries after mid-body disconnect and completes the file`() = runBlocking {
        withTimeout(60_000) {
            val (dispatcher, requests) = handler(listOf("ETag" to etag)) { resumedSlice(it) }
            val server = startServer(dispatcher)
            val target = File(newWorkDir(), "out.bin")

            Fetcher.downloadFile(listOf(server.url("/f").toString()), target)

            assertTrue(requests.size >= 2)
            if (requests[1].headers["Range"] != null) {

                assertEquals(etag, requests[1].headers["If-Range"])
            }
            assertArrayEquals(content, target.readBytes())
        }
    }

    @Test
    fun `416 discards resume context and retries without range`() = runBlocking {
        withTimeout(60_000) {
            val (dispatcher, requests) = handler(listOf("ETag" to etag)) { _ ->
                MockResponse.Builder().code(416).build()
            }
            val server = startServer(dispatcher)
            val target = File(newWorkDir(), "out.bin")

            Fetcher.downloadFile(listOf(server.url("/f").toString()), target)

            assertArrayEquals(content, target.readBytes())
            assertRangeEmittedAtMostOnce(requests)
        }
    }

    @Test
    fun `full 200 answer to a range request restarts from scratch`() = runBlocking {
        withTimeout(60_000) {
            val (dispatcher, requests) = handler(listOf("ETag" to etag)) { _ ->
                completeResponse(listOf("ETag" to etag))
            }
            val server = startServer(dispatcher)
            val target = File(newWorkDir(), "out.bin")

            Fetcher.downloadFile(listOf(server.url("/f").toString()), target)

            assertArrayEquals(content, target.readBytes())
            assertRangeEmittedAtMostOnce(requests)
        }
    }

    @Test
    fun `weak etag never emits range request`() = runBlocking {
        withTimeout(60_000) {
            val (dispatcher, requests) = handler(listOf("ETag" to "W/\"weak-42\"")) { resumedSlice(it) }
            val server = startServer(dispatcher)
            val target = File(newWorkDir(), "out.bin")

            Fetcher.downloadFile(listOf(server.url("/f").toString()), target)

            requests.forEach { assertNull(it.headers["Range"]) }
            assertArrayEquals(content, target.readBytes())
        }
    }

    @Test
    fun `mismatched content range answer restarts from scratch`() = runBlocking {
        withTimeout(60_000) {
            val (dispatcher, requests) = handler(listOf("ETag" to etag)) { request ->

                val claimedStart = request.rangeStart!! + 1024
                MockResponse.Builder()
                    .code(206)
                    .addHeader("accept-ranges", "bytes")
                    .addHeader("ETag", etag)
                    .addHeader("Content-Range", "bytes $claimedStart-${content.size}/${content.size}")
                    .body(Buffer().write(content))
                    .build()
            }
            val server = startServer(dispatcher)
            val target = File(newWorkDir(), "out.bin")

            Fetcher.downloadFile(listOf(server.url("/f").toString()), target)

            assertArrayEquals(content, target.readBytes())
            assertRangeEmittedAtMostOnce(requests)
        }
    }

    private fun assertRangeEmittedAtMostOnce(requests: List<RecordedRequest>) {
        val rangeIndexes = requests.indices.filter { requests[it].headers["Range"] != null }
        assertTrue(rangeIndexes.size <= 1)
        rangeIndexes.firstOrNull()?.let { index ->
            if (index + 1 < requests.size) {
                assertNull(requests[index + 1].headers["Range"])
            }
        }
    }

    companion object {
        private val RANGE_PATTERN = Regex("""bytes=(\d+)-""")
    }
}
