// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.resources.runtime.writeResourceFileAtomically
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ResourceFileAtomicWriteTest {
    @Test
    fun replacesExistingFileAndLeavesNoTemporaryFile() {
        val dir = Files.createTempDirectory("atomic").toFile()
        try {
            val target = dir.resolve("geoip.dat")
            target.writeText("old")
            writeResourceFileAtomically(target) { it.write("new".toByteArray()) }
            assertEquals("new", target.readText())
            assertEquals(listOf("geoip.dat"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyWriteFailsKeepsOldContentAndCleansUp() {
        val dir = Files.createTempDirectory("atomic").toFile()
        try {
            val target = dir.resolve("a")
            target.writeText("old")
            assertFailsWith<IllegalStateException> { writeResourceFileAtomically(target) { } }
            assertEquals("old", target.readText())
            assertEquals(listOf("a"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeFailureCleansUpAndCustomReplaceIsUsed() {
        val dir = Files.createTempDirectory("atomic").toFile()
        try {
            val target = dir.resolve("b.dat")
            assertFailsWith<java.io.IOException> {
                writeResourceFileAtomically(target) { throw java.io.IOException("boom") }
            }
            assertFalse(target.exists())
            writeResourceFileAtomically(target, replace = { s, t -> s.copyTo(t, overwrite = true) }) {
                it.write(1)
            }
            assertEquals(1, target.length())
            assertEquals(listOf("b.dat"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }
}
