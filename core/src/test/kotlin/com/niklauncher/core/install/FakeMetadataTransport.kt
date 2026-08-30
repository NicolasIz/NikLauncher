package com.niklauncher.core.install

import com.niklauncher.core.download.HttpBody
import com.niklauncher.core.download.HttpTransport
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Serves both metadata documents and file payloads from memory, recording every
 * request so tests can assert on caching and on what was actually fetched.
 */
class FakeMetadataTransport(
    private val documents: MutableMap<String, ByteArray> = mutableMapOf(),
) : HttpTransport {

    val requestedUrls = mutableListOf<String>()

    /** When true every request fails, standing in for being offline. */
    var offline: Boolean = false

    fun put(url: String, body: String) {
        documents[url] = body.toByteArray(Charsets.UTF_8)
    }

    fun putBinary(url: String, body: ByteArray) {
        documents[url] = body
    }

    fun remove(url: String) {
        documents.remove(url)
    }

    override suspend fun open(url: String, rangeFrom: Long?): HttpBody {
        synchronized(requestedUrls) { requestedUrls += url }
        if (offline) throw IOException("simulated offline")
        val body = documents[url] ?: return Body(404, ByteArray(0))
        return Body(200, body)
    }

    private class Body(
        override val statusCode: Int,
        private val payload: ByteArray,
    ) : HttpBody {
        override val contentLength: Long get() = payload.size.toLong()
        override val resumed: Boolean get() = false
        override fun stream(): InputStream = ByteArrayInputStream(payload)
        override fun close() = Unit
    }
}
