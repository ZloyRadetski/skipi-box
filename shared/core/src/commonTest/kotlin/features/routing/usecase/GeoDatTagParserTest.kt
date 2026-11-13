// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import features.logs.AppLog
import features.logs.AppLogLevel
import features.logs.AppLogSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeoDatTagParserTest {
    @Test
    fun extractsDistinctTagsAndSkipsUnrelatedFields() {
        val bytes = byteArrayOf(
            *entry("cn", byteArrayOf(0x12, 0x03, 1, 2, 3)),
            *entry("us"),
            *entry("cn"),
        )
        var offset = 0
        val result = GeoDatTagParser.parseTags(
            readByte = { if (offset < bytes.size) bytes[offset++].toInt() and 0xff else -1 },
            skipBytes = { count ->
                val skipped = minOf(count, (bytes.size - offset).toLong()).toInt()
                offset += skipped
                skipped.toLong()
            },
        )
        assertEquals(listOf("cn", "us"), result)
    }

    @Test
    fun aReadFailureKeepsTheTagsSoFarAndIsLoggedAsAWarning() {
        val bytes = entry("telegram")
        var offset = 0
        val warnings = mutableListOf<Throwable?>()
        AppLog.install(object : AppLogSink {
            override fun log(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean) {
                if (level == AppLogLevel.Warning) warnings += error
            }
        })
        try {
            val failure = IllegalStateException("broken dat")
            val result = GeoDatTagParser.parseTags(
                readByte = { if (offset < bytes.size) bytes[offset++].toInt() and 0xff else throw failure },
                skipBytes = { 0L },
            )
            assertEquals(listOf("telegram"), result)
            assertTrue(warnings.any { it === failure }, "the swallowed failure must be logged")
        } finally {
            AppLog.reset()
        }
    }

    @Test
    fun aFatalErrorIsNotSwallowed() {
        class Fatal : Error()
        assertFailsWith<Fatal> {
            GeoDatTagParser.parseTags(readByte = { throw Fatal() }, skipBytes = { 0L })
        }
    }

    @Test
    fun returnsTagsParsedBeforeTruncatedInput() {
        val valid = entry("telegram")
        val bytes = valid + byteArrayOf(0x0a, 0x7f)
        var offset = 0
        val result = GeoDatTagParser.parseTags(
            readByte = { if (offset < bytes.size) bytes[offset++].toInt() and 0xff else -1 },
            skipBytes = { 0L },
        )
        assertEquals(listOf("telegram"), result)
    }

    private fun entry(tag: String, extra: ByteArray = byteArrayOf()): ByteArray {
        val tagBytes = tag.encodeToByteArray()
        val body = byteArrayOf(0x0a, tagBytes.size.toByte()) + tagBytes + extra
        return byteArrayOf(0x0a, body.size.toByte()) + body
    }
}
