// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RawDnsWireTest {
    private class Bytes {
        private val data = mutableListOf<Byte>()
        fun byte(v: Int) { data += v.toByte() }
        fun short(v: Int) { byte(v shr 8 and 0xFF); byte(v and 0xFF) }
        fun bytes(v: ByteArray) { data += v.toList() }
        fun toByteArray() = data.toByteArray()
    }

    @Test
    fun buildQueryFormatsDnsQuestionCorrectly() {
        val query = RawDnsWire.buildQuery(0x1234, "example.com")

        assertTrue(query.size > 12)
        assertEquals(0x12.toByte(), query[0])
        assertEquals(0x34.toByte(), query[1])
        assertEquals(0x01.toByte(), query[2])
        assertEquals(0x00.toByte(), query[3])
        assertEquals(0x00.toByte(), query[4])
        assertEquals(0x01.toByte(), query[5])
        assertEquals(7.toByte(), query[12])
        assertEquals("example", query.decodeToString(13, 20))
        assertEquals(3.toByte(), query[20])
        assertEquals("com", query.decodeToString(21, 24))
        assertEquals(0.toByte(), query[24])
        assertEquals(0x00.toByte(), query[25])
        assertEquals(0x10.toByte(), query[26])
        assertEquals(0x00.toByte(), query[27])
        assertEquals(0x01.toByte(), query[28])
    }

    @Test
    fun parseTxtRecordsExtractsTxtStrings() {
        val out = Bytes()
        val txId = 0xABCD
        out.short(txId)
        out.short(0x8180)
        out.short(1)
        out.short(1)
        out.short(0)
        out.short(0)
        for (label in listOf("echo", "google", "com")) {
            val bytes = label.encodeToByteArray()
            out.byte(bytes.size)
            out.bytes(bytes)
        }
        out.byte(0)
        out.short(16)
        out.short(1)
        out.short(0xC00C)
        out.short(16)
        out.short(1)
        out.byte(0); out.byte(0); out.byte(0); out.byte(60)
        val txt1 = "1.2.3.4".encodeToByteArray()
        val txt2 = "edns0-client-subnet 1.2.3.0/24".encodeToByteArray()
        out.short(1 + txt1.size + 1 + txt2.size)
        out.byte(txt1.size); out.bytes(txt1)
        out.byte(txt2.size); out.bytes(txt2)

        val response = out.toByteArray()
        val records = RawDnsWire.parseTxtRecords(response, response.size, txId)

        assertNotNull(records)
        assertEquals(listOf("1.2.3.4", "edns0-client-subnet 1.2.3.0/24"), records)
    }

    @Test
    fun parseTxtRecordsRejectsMismatchedIdOrErrorRcode() {
        val out = Bytes()
        out.short(0x1111)
        out.short(0x8183)
        out.short(1)
        out.short(0)
        out.short(0)
        out.short(0)
        val response = out.toByteArray()

        assertNull(RawDnsWire.parseTxtRecords(response, response.size, 0x2222))
        assertNull(RawDnsWire.parseTxtRecords(response, response.size, 0x1111))
    }
}
