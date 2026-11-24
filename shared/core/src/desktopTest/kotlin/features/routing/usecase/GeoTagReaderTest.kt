// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GeoTagReaderTest {
    @Test
    fun missingAndEmptyFilesYieldNoTags() {
        val dir = Files.createTempDirectory("geotags").toFile()
        try {
            dir.resolve("empty.dat").writeBytes(ByteArray(0))
            val read = geoTagReader(dir)
            assertEquals(emptyList(), read("missing.dat"))
            assertEquals(emptyList(), read("empty.dat"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun undecodableFileYieldsNoTagsWithoutThrowing() {
        val dir = Files.createTempDirectory("geotags").toFile()
        try {
            dir.resolve("junk.dat").writeBytes(byteArrayOf(0x7f, 0x01, 0x02))
            assertEquals(emptyList(), geoTagReader(dir)("junk.dat"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
