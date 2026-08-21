// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class RawDnsClientTest {

    @Test
    fun buildQuery_formats_dns_question_correctly() {
        val query = RawDnsClient.buildQuery(0x1234, "example.com")
        // Header is 12 bytes
        assertTrue(query.size > 12)
        // Check transaction ID (0x1234)
        assertEquals(0x12.toByte(), query[0])
        assertEquals(0x34.toByte(), query[1])
        // Flags: 0x0100 (RD = 1)
        assertEquals(0x01.toByte(), query[2])
        assertEquals(0x00.toByte(), query[3])
        // QDCOUNT = 1
        assertEquals(0x00.toByte(), query[4])
        assertEquals(0x01.toByte(), query[5])

        // First label: length 7, "example"
        assertEquals(7.toByte(), query[12])
        val exampleStr = String(query, 13, 7, Charsets.US_ASCII)
        assertEquals("example", exampleStr)

        // Second label: length 3, "com"
        assertEquals(3.toByte(), query[20])
        val comStr = String(query, 21, 3, Charsets.US_ASCII)
        assertEquals("com", comStr)

        // Root label: 0
        assertEquals(0.toByte(), query[24])

        // Type TXT (16 = 0x0010)
        assertEquals(0x00.toByte(), query[25])
        assertEquals(0x10.toByte(), query[26])

        // Class IN (1 = 0x0001)
        assertEquals(0x00.toByte(), query[27])
        assertEquals(0x01.toByte(), query[28])
    }

    @Test
    fun parseTxtRecords_extracts_txt_strings() {
        val out = ByteArrayOutputStream()
        fun writeShort(v: Int) {
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        val txId = 0xABCD
        writeShort(txId)
        writeShort(0x8180) // Flags: QR=1 (response), AA=0, RD=1, RA=1, RCODE=0
        writeShort(1) // QDCOUNT
        writeShort(1) // ANCOUNT
        writeShort(0) // NSCOUNT
        writeShort(0) // ARCOUNT

        // Question: "echo.google.com", Type TXT, Class IN
        for (label in listOf("echo", "google", "com")) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        writeShort(16) // TXT
        writeShort(1) // IN

        // Answer: pointer to question name (0xC00C)
        writeShort(0xC00C)
        writeShort(16) // Type TXT
        writeShort(1) // Class IN
        out.write(0) // TTL 4 bytes
        out.write(0)
        out.write(0)
        out.write(60)
        val txtData1 = "1.2.3.4".toByteArray(Charsets.UTF_8)
        val txtData2 = "edns0-client-subnet 1.2.3.0/24".toByteArray(Charsets.UTF_8)
        val rdLength = 1 + txtData1.size + 1 + txtData2.size
        writeShort(rdLength)
        out.write(txtData1.size)
        out.write(txtData1)
        out.write(txtData2.size)
        out.write(txtData2)

        val response = out.toByteArray()
        val records = RawDnsClient.parseTxtRecords(response, response.size, txId)

        assertNotNull(records)
        assertEquals(2, records!!.size)
        assertEquals("1.2.3.4", records[0])
        assertEquals("edns0-client-subnet 1.2.3.0/24", records[1])
    }

    @Test
    fun parseTxtRecords_rejects_mismatched_id_or_error_rcode() {
        val out = ByteArrayOutputStream()
        fun writeShort(v: Int) {
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        writeShort(0x1111)
        writeShort(0x8183) // RCODE = 3 (NXDOMAIN)
        writeShort(1)
        writeShort(0)
        writeShort(0)
        writeShort(0)

        val response = out.toByteArray()
        // Mismatched ID
        assertNull(RawDnsClient.parseTxtRecords(response, response.size, 0x2222))
        // Matched ID but RCODE != 0
        assertNull(RawDnsClient.parseTxtRecords(response, response.size, 0x1111))
    }
}
