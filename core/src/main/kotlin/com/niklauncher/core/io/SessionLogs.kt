package com.niklauncher.core.io

import java.io.File

/**
 * The session logs, and the small amount of management they need.
 *
 * A session log is the only account of what happened when the game ran: the
 * VM's own output is redirected into it before HotSpot starts, so it holds
 * both "the VM could not be created, and here is why" and whatever Minecraft
 * printed before it stopped. When the game calls System.exit the process dies
 * without the launcher ever rendering a failure, and this file is all that is
 * left - so it has to survive the process, and it has to be findable
 * afterwards from the launcher's own process.
 */
class SessionLogs(private val directory: File) {

    /** One log, with what a person needs to tell two of them apart. */
    data class Entry(
        val file: File,
        val sizeBytes: Long,
        val modifiedEpochMillis: Long,
        /** True for the copy kept from the run before this one. */
        val previous: Boolean,
    ) {
        val name: String get() = file.name
    }

    /**
     * The log for a session, rotated so the file the game is about to write is
     * only ever this run.
     *
     * Output is appended rather than truncated, because a crash between the
     * open and the first write would otherwise leave nothing at all - so
     * without rotation a log would grow across every session and its tail
     * would mix runs, which is worse than useless when the question is what
     * just happened. The run before is kept: a session that dies on startup is
     * often explained by what the previous one did.
     */
    fun beginSession(instanceId: String): File {
        val log = logFor(instanceId)
        log.parentFile?.mkdirs()
        if (log.isFile) {
            val kept = previousOf(log)
            kept.delete()
            if (!log.renameTo(kept)) log.delete()
        }
        return log
    }

    fun logFor(instanceId: String): File = File(directory, "session-$instanceId.log")

    /** Every session log, newest first, so "the last one" needs no bookkeeping. */
    fun all(): List<Entry> {
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.name.startsWith(PREFIX) && it.length() > 0 }
            .map {
                Entry(
                    file = it,
                    sizeBytes = it.length(),
                    modifiedEpochMillis = it.lastModified(),
                    previous = it.name.endsWith(PREVIOUS_SUFFIX),
                )
            }
            .sortedWith(compareByDescending<Entry> { it.modifiedEpochMillis }.thenBy { it.name })
    }

    fun latest(): Entry? = all().firstOrNull()

    /**
     * The end of a log, which is where the reason lives.
     *
     * Read by lines from the end rather than the whole file, because a session
     * that ran for a while can leave megabytes and the launcher only ever
     * shows the tail.
     */
    fun tail(file: File, lines: Int = DEFAULT_TAIL_LINES): String? = runCatching {
        if (!file.isFile) return@runCatching null
        file.useLines { sequence ->
            val window = ArrayDeque<String>(lines)
            sequence.forEach {
                if (window.size == lines) window.removeFirst()
                window.addLast(it)
            }
            window.joinToString("\n").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun previousOf(log: File) = File(log.parentFile, log.name + PREVIOUS_SUFFIX)

    companion object {
        const val DEFAULT_TAIL_LINES = 60
        private const val PREFIX = "session-"
        private const val PREVIOUS_SUFFIX = ".1"
    }
}
