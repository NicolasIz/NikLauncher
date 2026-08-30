package com.niklauncher.core.library

/**
 * Orders Maven version strings well enough to pick the newest duplicate.
 *
 * Mod loaders routinely pull several versions of the same library into one
 * classpath; whichever we keep must be the newest, because the oldest usually
 * lacks methods the newer callers expect. Numeric segments compare numerically
 * so that `1.10 > 1.9`, and a version with a qualifier (`1.0-rc1`) sorts below
 * the bare release (`1.0`).
 */
object VersionComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        val left = split(a)
        val right = split(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrNull(i)
            val r = right.getOrNull(i)
            // A missing segment means the shorter version is a release prefix.
            if (l == null) return if (r!!.isQualifier) 1 else -1
            if (r == null) return if (l.isQualifier) -1 else 1
            val cmp = l.compareTo(r)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private data class Segment(val number: Long?, val text: String) : Comparable<Segment> {
        val isQualifier: Boolean get() = number == null

        override fun compareTo(other: Segment): Int = when {
            number != null && other.number != null -> number.compareTo(other.number)
            // Numeric segments outrank qualifiers: 1.0 is newer than 1.0-rc1.
            number != null -> 1
            other.number != null -> -1
            else -> text.compareTo(other.text)
        }
    }

    private fun split(version: String): List<Segment> =
        version.split('.', '-', '_', '+')
            .filter { it.isNotEmpty() }
            .map { part -> Segment(part.toLongOrNull(), part.lowercase()) }
}
