package com.niklauncher.core.library

import kotlin.test.Test
import kotlin.test.assertTrue

class VersionComparatorTest {

    private fun assertNewer(newer: String, older: String) {
        assertTrue(
            VersionComparator.compare(newer, older) > 0,
            "expected $newer > $older",
        )
    }

    @Test
    fun `compares numeric segments numerically`() {
        assertNewer("1.10", "1.9")
        assertNewer("9.5", "9.4")
        assertNewer("2.0", "1.99")
    }

    @Test
    fun `longer release version wins over prefix`() {
        assertNewer("1.0.1", "1.0")
    }

    @Test
    fun `release outranks its own qualifier`() {
        assertNewer("1.0", "1.0-rc1")
        assertNewer("1.0", "1.0-SNAPSHOT")
    }

    @Test
    fun `equal versions compare equal`() {
        assertTrue(VersionComparator.compare("3.2.1", "3.2.1") == 0)
    }
}
