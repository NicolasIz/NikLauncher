package com.niklauncher.core.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-1 helpers.
 *
 * Mojang publishes a SHA-1 for essentially every file it serves, and NikLauncher
 * verifies all of them. A truncated download that survives verification turns
 * into a crash deep inside the JVM later, where it is far harder to diagnose.
 */
object Hashing {

    private const val BUFFER_SIZE = 64 * 1024

    fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(bytes).toHex()

    fun sha1(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun sha1(file: File): String = file.inputStream().buffered().use { sha1(it) }

    /**
     * True when [file] exists and matches [expectedSha1]. A null or blank
     * expectation falls back to a plain existence check, since some
     * mod-loader manifests omit hashes.
     */
    fun verify(file: File, expectedSha1: String?, expectedSize: Long = 0): Boolean {
        if (!file.isFile) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        if (expectedSha1.isNullOrBlank()) return true
        return sha1(file).equals(expectedSha1, ignoreCase = true)
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
