// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals

class GeoDatParserFileTest {
    @Test
    fun missingAndEmptyFilesHaveNoTags() = withTempDirectory { root ->
        val missing = File(root, "missing.dat")
        val empty = File(root, "empty.dat").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf())
        }

        assertEquals(emptyList(), GeoDatParser.parseTags(missing))
        assertEquals(emptyList(), GeoDatParser.parseTags(empty))
    }

    @Test
    fun readsTagsFromAMiniatureGeoDatFile() = withTempDirectory { root ->
        val file = File(root, "geosite.dat")
        writeGeoDat(file, "telegram", "google")

        assertEquals(listOf("telegram", "google"), GeoDatParser.parseTags(file))
    }

    @Test
    fun cachedTagsAreRefreshedWhenSameLengthFileHasAChangedTimestamp() = withTempDirectory { root ->
        val file = File(root, "geoip.dat")
        writeGeoDat(file, "alpha")
        val firstLength = file.length()
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(1_700_000_000_000L))

        assertEquals(listOf("alpha"), GeoDatParser.parseTags(file))

        writeGeoDat(file, "bravo")
        assertEquals(firstLength, file.length())
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(1_700_000_030_000L))

        assertEquals(listOf("bravo"), GeoDatParser.parseTags(file))
    }

    @Test
    fun truncatingACachedFileToEmptyDoesNotReturnStaleTags() = withTempDirectory { root ->
        val file = File(root, "geosite.dat")
        writeGeoDat(file, "telegram")
        assertEquals(listOf("telegram"), GeoDatParser.parseTags(file))

        file.writeBytes(byteArrayOf())

        assertEquals(emptyList(), GeoDatParser.parseTags(file))
    }

    private fun writeGeoDat(file: File, vararg tags: String) {
        file.parentFile?.mkdirs()
        file.writeBytes(encodeGeoDat(*tags))
    }

    private fun encodeGeoDat(vararg tags: String): ByteArray = buildList {
        tags.forEach { tag ->
            val tagBytes = tag.encodeToByteArray()
            val entry = byteArrayOf(0x0a, tagBytes.size.toByte()) + tagBytes
            add(0x0a.toByte())
            add(entry.size.toByte())
            addAll(entry.asList())
        }
    }.toByteArray()

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("skipi-geodat-file-test-").toFile()
        try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
