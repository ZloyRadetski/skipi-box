// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopUnreadableLibraryTest {
    @Test
    fun anUnreadableLibraryIsCopiedAsideBeforeTheAppStartsEmpty() {
        // The app went on with an empty library and its next save wrote that over the user's servers.
        val directory = Files.createTempDirectory("skipi-library")
        val file = directory.resolve("servers.json")
        Files.writeString(file, "{ broken")

        val library = Result.failure<List<String>>(IllegalStateException("bad json"))
            .orEmptyKeepingUnreadable(file, now = { 1234L }) { emptyList() }

        assertEquals(emptyList(), library)
        assertEquals("{ broken", Files.readString(directory.resolve("servers.json.unreadable-1234")))
    }

    @Test
    fun aReadableLibraryIsReturnedAndNothingIsCopied() {
        val directory = Files.createTempDirectory("skipi-library")
        val file = directory.resolve("servers.json")
        Files.writeString(file, "[]")

        val library = Result.success(listOf("one")).orEmptyKeepingUnreadable(file) { emptyList() }

        assertEquals(listOf("one"), library)
        assertTrue(Files.list(directory).use { files -> files.count() } == 1L)
    }
}
