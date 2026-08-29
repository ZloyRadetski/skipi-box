// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProfileFilesTest {
    @Test
    fun profileTextRoundTripsAsUtf8WithoutImplicitMutation() {
        val path = Files.createTempFile("skipi-profile-", ".conf")
        try {
            val original = "[General]\ndns-server = 1.1.1.1\n# Привет\n"

            DesktopProfileFiles.write(path, original).getOrThrow()

            assertEquals(original, DesktopProfileFiles.read(path).getOrThrow())
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
