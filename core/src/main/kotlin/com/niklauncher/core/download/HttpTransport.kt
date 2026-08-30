package com.niklauncher.core.download

import java.io.Closeable
import java.io.InputStream

/**
 * The minimum HTTP surface the downloader needs.
 *
 * Keeping this an interface is what lets the whole download engine - resume
 * logic, hash verification, retry behaviour - be tested deterministically
 * without a network, and lets the Android module supply an OkHttp-backed
 * implementation without :core depending on it.
 */
interface HttpTransport {
    /**
     * Opens [url] for reading. When [rangeFrom] is non-null the caller is
     * resuming and wants bytes from that offset onward; an implementation that
     * cannot honour it must still succeed and report [HttpBody.resumed] false.
     */
    suspend fun open(url: String, rangeFrom: Long? = null): HttpBody
}

interface HttpBody : Closeable {
    val statusCode: Int

    /** Length of this response body, or null when the server did not say. */
    val contentLength: Long?

    /** True when the server answered a range request with 206 Partial Content. */
    val resumed: Boolean

    fun stream(): InputStream
}

class HttpStatusException(
    val url: String,
    val status: Int,
) : Exception("HTTP $status for $url") {

    /**
     * Retrying a 4xx other than 408/429 just burns battery: the server has told
     * us the request itself is wrong.
     */
    val isRetryable: Boolean
        get() = status !in 400..499 || status == 408 || status == 429
}
