package com.niklauncher.core.download

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Scriptable transport for exercising the downloader without a network.
 *
 * It can serve byte ranges, refuse them, truncate a response mid-stream, or
 * fail a set number of times before succeeding - which is what makes the resume
 * and retry paths testable at all.
 */
class FakeHttpTransport(
    private val content: Map<String, ByteArray>,
    private val supportsRanges: Boolean = true,
    /** Cut the body off after this many bytes, simulating a dropped connection. */
    private val truncateAfter: Int? = null,
    /** Fail this many times before serving normally. */
    private var failuresBeforeSuccess: Int = 0,
    private val statusOverride: Int? = null,
) {
    var openCount: Int = 0
        private set

    var lastRangeRequested: Long? = null
        private set

    fun asTransport(): HttpTransport = object : HttpTransport {
        override suspend fun open(url: String, rangeFrom: Long?): HttpBody {
            openCount++
            lastRangeRequested = rangeFrom

            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                throw IOException("simulated network failure")
            }

            val body = content[url] ?: return FakeBody(404, ByteArray(0), false)
            statusOverride?.let { return FakeBody(it, ByteArray(0), false) }

            val honourRange = supportsRanges && rangeFrom != null && rangeFrom < body.size
            val payload = if (honourRange) body.copyOfRange(rangeFrom!!.toInt(), body.size) else body
            val delivered = truncateAfter?.let { payload.copyOfRange(0, minOf(it, payload.size)) } ?: payload

            return FakeBody(
                statusCode = if (honourRange) 206 else 200,
                payload = delivered,
                resumed = honourRange,
            )
        }
    }

    private class FakeBody(
        override val statusCode: Int,
        private val payload: ByteArray,
        override val resumed: Boolean,
    ) : HttpBody {
        override val contentLength: Long get() = payload.size.toLong()
        override fun stream(): InputStream = ByteArrayInputStream(payload)
        override fun close() = Unit
    }
}
