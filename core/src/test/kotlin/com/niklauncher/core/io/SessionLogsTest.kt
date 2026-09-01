package com.niklauncher.core.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These logs are the only thing a player can send back from a failed session,
 * so what they contain is not a detail: a tail that mixes two runs points at
 * the wrong cause, and a log the launcher cannot find after the game's process
 * died is the same as no log at all.
 */
class SessionLogsTest {

    private val directory: File = Files.createTempDirectory("niklogs").toFile()
    private val logs = SessionLogs(directory)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a session starts with a log of its own`() {
        val first = logs.beginSession("alpha")
        first.writeText("first run\n")

        val second = logs.beginSession("alpha")

        assertEquals(first, second)
        assertFalse(second.isFile, "the new session must not inherit the last one's output")
    }

    @Test
    fun `the run before is kept`() {
        logs.beginSession("alpha").writeText("first run\n")
        logs.beginSession("alpha").writeText("second run\n")

        val kept = File(directory, "session-alpha.log.1")
        assertTrue(kept.isFile, "the previous run should still be on disk")
        assertEquals("first run\n", kept.readText())
    }

    @Test
    fun `only two generations are kept`() {
        repeat(4) { run ->
            logs.beginSession("alpha").writeText("run $run\n")
        }

        val kept = directory.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("session-alpha.log", "session-alpha.log.1"), kept)
        assertEquals("run 2\n", File(directory, "session-alpha.log.1").readText())
    }

    @Test
    fun `empty logs are not offered`() {
        logs.beginSession("alpha")

        assertTrue(logs.all().isEmpty(), "a log with nothing in it explains nothing")
        assertNull(logs.latest())
    }

    @Test
    fun `the newest log comes first`() {
        val older = logs.beginSession("alpha").apply { writeText("alpha\n") }
        older.setLastModified(1_000_000L)
        val newer = logs.beginSession("beta").apply { writeText("beta\n") }
        newer.setLastModified(2_000_000L)

        val all = logs.all()

        assertEquals(listOf("session-beta.log", "session-alpha.log"), all.map { it.name })
        assertEquals("session-beta.log", logs.latest()?.name)
        assertEquals(5L, all.first().sizeBytes)
        assertFalse(all.first().previous)
    }

    @Test
    fun `a kept log is marked as the run before`() {
        logs.beginSession("alpha").writeText("first\n")
        logs.beginSession("alpha").writeText("second\n")

        val kept = logs.all().single { it.name == "session-alpha.log.1" }

        assertTrue(kept.previous)
    }

    @Test
    fun `the tail is the end of the file`() {
        val log = logs.beginSession("alpha")
        log.writeText((1..500).joinToString("\n") { "line $it" })

        val tail = logs.tail(log, lines = 3)

        assertEquals("line 498\nline 499\nline 500", tail)
    }

    @Test
    fun `a short log is returned whole`() {
        val log = logs.beginSession("alpha")
        log.writeText("only line")

        assertEquals("only line", logs.tail(log, lines = 60))
    }

    @Test
    fun `a log that is not there is not an error`() {
        assertNull(logs.tail(File(directory, "session-missing.log")))
    }

    /**
     * The system log is listed and rotated like the session's own, because it
     * is the half that survives a native crash - the VM's output stops mid
     * sentence, and what killed the process is only in this one.
     */
    @Test
    fun `the system log is kept beside the session's own`() {
        val session = logs.beginSession("alpha").apply { writeText("vm output\n") }
        val system = logs.beginSystemLog("alpha").apply { writeText("F DEBUG: signal 11\n") }

        assertTrue(session != system, "they must be separate files")
        val names = logs.all().map { it.name }.toSet()
        assertEquals(setOf("session-alpha.log", "session-alpha.system.log"), names)
    }

    @Test
    fun `the system log rotates on its own`() {
        logs.beginSystemLog("alpha").writeText("first\n")
        logs.beginSystemLog("alpha").writeText("second\n")

        assertEquals("first\n", File(directory, "session-alpha.system.log.1").readText())
        assertEquals("second\n", File(directory, "session-alpha.system.log").readText())
    }
}
