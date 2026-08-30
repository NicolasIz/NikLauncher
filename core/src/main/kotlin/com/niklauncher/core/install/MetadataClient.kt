package com.niklauncher.core.install

import com.niklauncher.core.download.HttpStatusException
import com.niklauncher.core.download.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads JSON metadata documents over the shared [HttpTransport].
 *
 * Separate from [com.niklauncher.core.download.Downloader] because metadata is
 * small, read into memory and never resumed, while the downloader is built
 * around large files on disk.
 */
class MetadataClient(
    private val transport: HttpTransport,
    /**
     * Hard ceiling on a metadata document. Mojang's manifest is around 1 MB and
     * the largest asset index well under 10 MB; the cap stops a misbehaving or
     * hostile endpoint from streaming until the app is killed.
     */
    private val maxBytes: Long = 32L * 1024 * 1024,
) {

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        transport.open(url, null).use { body ->
            if (body.statusCode !in 200..299) throw HttpStatusException(url, body.statusCode)
            body.stream().readBoundedText(url)
        }
    }

    private fun InputStream.readBoundedText(url: String): String {
        val buffer = ByteArray(64 * 1024)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("Metadata document at $url exceeds $maxBytes bytes")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8)
    }
}
