package com.movtery.cmclauncher.game.download.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class ResumeContextTest {

    private val url = URL("https://example.test/file.bin")

    private fun response(
        code: Int = 200,
        path: String = "/file.bin",
        headers: Map<String, String> = emptyMap()
    ): ResponseInfo = ResponseInfo(
        code = code,
        url = URL("https://example.test$path"),
        headers = headers.mapValues { listOf(it.value) }
    )

    private fun established(
        headers: Map<String, String> = mapOf(
            "ETag" to "\"a\"", "accept-ranges" to "bytes", "Content-Length" to "10"
        )
    ): ResumeContext = ResumeContext.of(response(headers = headers))!!

    @Test
    fun `of requires 200 with ranges identity length and a validator`() {
        assertNull(ResumeContext.of(response(code = 206)))
        assertNull(ResumeContext.of(response(headers = mapOf("ETag" to "\"a\""))))
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf(
                        "ETag" to "\"a\"", "accept-ranges" to "bytes",
                        "Content-Length" to "10", "Content-Encoding" to "gzip"
                    )
                )
            )
        )
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf("ETag" to "\"a\"", "accept-ranges" to "bytes", "Content-Length" to "abc")
                )
            )
        )

        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf("ETag" to "W/\"a\"", "accept-ranges" to "bytes", "Content-Length" to "10")
                )
            )
        )
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf("accept-ranges" to "bytes", "Content-Length" to "10")
                )
            )
        )

        val established = ResumeContext.of(
            response(
                headers = mapOf(
                    "ETag" to "\"a\"", "accept-ranges" to "bytes",
                    "Content-Length" to "10", "Last-Modified" to "Tue, 15 Nov 2099 12:00:00 GMT"
                )
            )
        )
        assertTrue(established != null)
    }

    @Test
    fun `weak etag falls back to last-modified validator`() {
        val context = established(
            headers = mapOf(
                "ETag" to "W/\"a\"", "accept-ranges" to "bytes",
                "Content-Length" to "10", "Last-Modified" to "Tue, 15 Nov 2099 12:00:00 GMT"
            )
        )
        assertEquals("Tue, 15 Nov 2099 12:00:00 GMT", context.ifRange())
    }

    @Test
    fun `canResume validates code encoding length etag and content range`() {
        val context = established()
        context.addBytes(4)

        assertFalse(
            context.canResume(200, response(headers = partialHeaders(6)))
        )

        assertFalse(
            context.canResume(
                206, response(code = 206, headers = partialHeaders(6) + ("Content-Encoding" to "gzip"))
            )
        )

        assertFalse(
            context.canResume(206, response(code = 206, headers = partialHeaders(100)))
        )

        assertFalse(
            context.canResume(
                206, response(code = 206, headers = partialHeaders(6) + ("ETag" to "\"b\""))
            )
        )

        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "ETag" to "\"a\"", "Content-Length" to "6",
                        "Content-Range" to "bytes 5-9/10"
                    )
                )
            )
        )

        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "ETag" to "\"a\"", "Content-Length" to "6",
                        "Content-Range" to "bytes 4-7/10"
                    )
                )
            )
        )

        assertTrue(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "ETag" to "\"a\"", "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )
    }

    @Test
    fun `canResume with last-modified validator requires same url`() {
        val lastModified = "Tue, 15 Nov 2099 12:00:00 GMT"
        val context = established(
            headers = mapOf(
                "Last-Modified" to lastModified, "accept-ranges" to "bytes", "Content-Length" to "10"
            )
        )
        context.addBytes(4)

        assertTrue(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "Last-Modified" to lastModified, "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )

        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    path = "/elsewhere.bin",
                    headers = mapOf(
                        "Last-Modified" to lastModified, "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )

        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "Last-Modified" to "Wed, 16 Nov 2099 12:00:00 GMT", "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )
    }

    @Test
    fun `hasPartialContent is exclusive of both empty and complete`() {
        val context = established()
        assertFalse(context.hasPartialContent())
        context.addBytes(4)
        assertTrue(context.hasPartialContent())
        context.addBytes(6)
        assertFalse(context.hasPartialContent())
    }

    private fun partialHeaders(remaining: Long): Map<String, String> = mapOf(
        "ETag" to "\"a\"", "Content-Length" to remaining.toString(),
        "Content-Range" to "bytes 4-${4 + remaining - 1}/10"
    )
}
