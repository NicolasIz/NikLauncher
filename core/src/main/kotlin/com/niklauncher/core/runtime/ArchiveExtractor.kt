package com.niklauncher.core.runtime

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Unpacks a downloaded runtime pack.
 *
 * Written against the JDK's own zip support plus a small tar reader rather than
 * pulling in a compression library, because :core is shared with the Android
 * module and every dependency there costs method count and APK size.
 *
 * The security posture matters more than the format support: these archives
 * come off the network, and an entry named `../../../lib/something.so` would
 * otherwise let a hostile pack write outside the runtime directory. Every entry
 * is resolved and checked before a single byte is written.
 */
object ArchiveExtractor {

    /** Refuses to write more than this, so a zip bomb cannot fill the device. */
    const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024

    private const val EXECUTABLE_MODE_BITS = 73 // 0o111

    data class Result(
        val filesWritten: Int,
        val bytesWritten: Long,
    )

    class UnsafeEntryException(val entryName: String) :
        IOException("Archive entry escapes the destination directory: '" + entryName + "'")

    class ArchiveTooLargeException(val limit: Long) :
        IOException("Archive expands beyond the " + limit + " byte limit")

    enum class Format { ZIP, TAR_GZ }

    /** Guesses the format from the file name; packs are named conventionally. */
    fun formatOf(fileName: String): Format? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".zip") -> Format.ZIP
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> Format.TAR_GZ
            else -> null
        }
    }

    fun extract(
        archive: File,
        destination: File,
        format: Format = formatOf(archive.name)
            ?: throw IOException("Unsupported archive format: " + archive.name),
        maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
    ): Result {
        destination.mkdirs()
        return archive.inputStream().buffered().use { stream ->
            when (format) {
                Format.ZIP -> extractZip(stream, destination, maxUncompressedBytes)
                Format.TAR_GZ -> extractTarGz(stream, destination, maxUncompressedBytes)
            }
        }
    }

    private fun extractZip(stream: InputStream, destination: File, limit: Long): Result {
        var files = 0
        var bytes = 0L
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = resolveSafely(destination, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    bytes += copyBounded(zip, target, limit - bytes)
                    files++
                }
                zip.closeEntry()
            }
        }
        return Result(files, bytes)
    }

    private fun extractTarGz(stream: InputStream, destination: File, limit: Long): Result {
        var files = 0
        var bytes = 0L
        GZIPInputStream(stream).use { gzip ->
            val tar = TarReader(gzip)
            while (true) {
                val entry = tar.nextEntry() ?: break
                when {
                    entry.isDirectory -> resolveSafely(destination, entry.name).mkdirs()

                    entry.isRegularFile -> {
                        val target = resolveSafely(destination, entry.name)
                        target.parentFile?.mkdirs()
                        bytes += tar.copyEntryTo(entry, target, limit - bytes)
                        // JDK archives carry the executable bit on their
                        // binaries and it has to survive extraction.
                        if (entry.mode and EXECUTABLE_MODE_BITS != 0) {
                            target.setExecutable(true, false)
                        }
                        files++
                    }

                    // Symlinks and device nodes are skipped rather than
                    // recreated: nothing in a runtime pack needs them, and both
                    // are ways to reach outside the destination.
                    else -> tar.skipEntry(entry)
                }
            }
        }
        return Result(files, bytes)
    }

    /**
     * Resolves an entry name inside [destination], rejecting anything that
     * would land outside it once `..` segments are applied.
     */
    private fun resolveSafely(destination: File, entryName: String): File {
        if (entryName.isBlank()) throw UnsafeEntryException(entryName)
        val root = destination.canonicalFile
        val candidate = File(root, entryName).canonicalFile
        if (candidate != root && !candidate.path.startsWith(root.path + File.separator)) {
            throw UnsafeEntryException(entryName)
        }
        return candidate
    }

    private fun copyBounded(source: InputStream, target: File, remaining: Long): Long {
        if (remaining <= 0) throw ArchiveTooLargeException(DEFAULT_MAX_UNCOMPRESSED_BYTES)
        var written = 0L
        val buffer = ByteArray(64 * 1024)
        target.outputStream().buffered().use { out ->
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                written += read
                if (written > remaining) {
                    out.flush()
                    target.delete()
                    throw ArchiveTooLargeException(DEFAULT_MAX_UNCOMPRESSED_BYTES)
                }
                out.write(buffer, 0, read)
            }
        }
        return written
    }
}
