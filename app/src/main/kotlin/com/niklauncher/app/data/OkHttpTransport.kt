package com.niklauncher.app.data

import com.niklauncher.core.NikLauncher
import com.niklauncher.core.download.HttpBody
import com.niklauncher.core.download.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * The production [HttpTransport], backed by OkHttp.
 *
 * :core deliberately does not depend on OkHttp; this is the one place the two
 * meet, which is what keeps the download engine testable off-device.
 */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient(),
) : HttpTransport {

    override suspend fun open(url: String, rangeFrom: Long?): HttpBody = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", NikLauncher.USER_AGENT)
        if (rangeFrom != null && rangeFrom > 0) {
            builder.header("Range", "bytes=$rangeFrom-")
        }
        OkHttpBody(client.newCall(builder.build()).execute())
    }

    private class OkHttpBody(private val response: Response) : HttpBody {
        override val statusCode: Int get() = response.code

        override val contentLength: Long?
            get() = response.body?.contentLength()?.takeIf { it >= 0 }

        /** 206 is the only answer that actually continues a partial file. */
        override val resumed: Boolean get() = response.code == 206

        // Not InputStream.nullInputStream(): that is only available from
        // API 33, and minSdk here is 31.
        override fun stream(): InputStream =
            response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))

        override fun close() = response.close()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Generous read timeout: asset objects are small but Mojang's CDN
            // can be slow to first byte on mobile networks.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
