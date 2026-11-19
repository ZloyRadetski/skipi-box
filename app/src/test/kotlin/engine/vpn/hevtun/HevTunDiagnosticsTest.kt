// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HevTunDiagnosticsTest {
    @Test
    fun readHevTunDiagnostics_handles_missing_empty_and_truncated_files() {
        assertEquals("", readHevTunDiagnostics(""))
        assertEquals("", readHevTunDiagnostics("/path/does/not/exist/tun.log"))

        val tempFile = File.createTempFile("diag-test", ".log")
        try {
            tempFile.writeText("")
            assertEquals("", readHevTunDiagnostics(tempFile.absolutePath))

            tempFile.writeText("Line 1\nLine 2\nLine 3")
            assertEquals("Line 1\nLine 2\nLine 3", readHevTunDiagnostics(tempFile.absolutePath))

            val longContent = "A".repeat(3000) + "\nTail line"
            val read = readHevTunDiagnostics(tempFile.apply { writeText(longContent) }.absolutePath, maxChars = 100)
            assertTrue(read.endsWith("Tail line"))
            assertTrue(read.length <= 100)

            tempFile.writeText(
                "DEBUG socks5 client auth private-user:private-password\n" +
                    "INFO socks5 server auth user: private-user pass: private-password\n" +
                    "ERROR: failed to initialize tunnel\n",
            )
            val safeDiagnostics = readHevTunDiagnostics(tempFile.absolutePath)
            assertEquals("ERROR: failed to initialize tunnel", safeDiagnostics)
            assertFalse(safeDiagnostics.contains("private-user"))
            assertFalse(safeDiagnostics.contains("private-password"))
        } finally {
            tempFile.delete()
        }
    }
}
