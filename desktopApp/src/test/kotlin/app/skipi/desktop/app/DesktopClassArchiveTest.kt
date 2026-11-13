// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopClassArchiveTest {
    @Test
    fun theFolderForTheClassArchiveIsCreatedUnderTheLocalAppDataFolder() {
        val localAppData = Files.createTempDirectory("local-app-data")
        try {
            val folder = ensureClassArchiveFolder(localAppData.toString(), isWindows = true)

            assertEquals(localAppData.resolve("SKIPI"), folder)
            assertTrue(folder!!.exists() && folder.isDirectory())
        } finally {
            localAppData.toFile().deleteRecursively()
        }
    }

    @Test
    fun anExistingFolderIsLeftAlone() {
        val localAppData = Files.createTempDirectory("local-app-data")
        try {
            val existing = Files.createDirectories(localAppData.resolve("SKIPI"))
            val marker = Files.createFile(existing.resolve("skipi.jsa"))

            ensureClassArchiveFolder(localAppData.toString(), isWindows = true)

            assertTrue(marker.exists())
        } finally {
            localAppData.toFile().deleteRecursively()
        }
    }

    @Test
    fun nothingIsCreatedOffWindowsOrWithoutAFolderOrWhenItCannotBeCreated() {
        val localAppData = Files.createTempDirectory("local-app-data")
        try {
            assertNull(ensureClassArchiveFolder(localAppData.toString(), isWindows = false))
            assertTrue(!localAppData.resolve("SKIPI").exists())
            assertNull(ensureClassArchiveFolder(null, isWindows = true))
            assertNull(ensureClassArchiveFolder("", isWindows = true))
            // A file where the folder must go: creating it fails, and the start must go on.
            val blocker = Files.createFile(localAppData.resolve("blocked"))
            assertNull(ensureClassArchiveFolder(blocker.toString(), isWindows = true))
        } finally {
            localAppData.toFile().deleteRecursively()
        }
    }
}
