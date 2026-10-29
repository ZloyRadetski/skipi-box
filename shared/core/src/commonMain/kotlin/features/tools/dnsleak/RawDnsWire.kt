// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

/**
 * Minimal DNS wire format for the leak test: builds a single TXT question and
 * parses the character strings of the first TXT answer. Transport (UDP, SOCKS,
 * DNS-over-TCP) belongs to the host.
 */
object RawDnsWire {
    const val DnsPort = 53
    const val MaxResponseBytes = 2048

    fun buildQuery(transactionId: Int, domain: String): ByteArray {
        val out = ArrayList<Byte>()
        fun writeShort(value: Int) {
            out.add(((value shr 8) and 0xFF).toByte())
            out.add((value and 0xFF).toByte())
        }
        writeShort(transactionId)
        writeShort(0x0100) // flags: recursion desired
        writeShort(1) // qdcount
        writeShort(0) // ancount
        writeShort(0) // nscount
        writeShort(0) // arcount
        domain.split('.').filter { it.isNotBlank() }.forEach { label ->
            val bytes = label.encodeToByteArray()
            out.add(bytes.size.toByte())
            bytes.forEach(out::add)
        }
        out.add(0) // root label terminator
        writeShort(DnsTypeTxt)
        writeShort(DnsClassIn)
        return out.toByteArray()
    }

    fun parseTxtRecords(
        response: ByteArray,
        length: Int,
        expectedId: Int,
    ): List<String>? {
        if (length < HeaderSize) return null
        fun byteAt(offset: Int): Int = response[offset].toInt() and 0xFF
        fun shortAt(offset: Int): Int = (byteAt(offset) shl 8) or byteAt(offset + 1)

        if (shortAt(0) != expectedId) return null
        val flags = shortAt(2)
        val isResponse = (flags and 0x8000) != 0
        val rcode = flags and 0x000F
        if (!isResponse || rcode != 0) return null
        val answerCount = shortAt(6)
        if (answerCount <= 0) return null

        var offset = HeaderSize
        // Skip all question entries.
        repeat(shortAt(4)) {
            offset = skipName(response, offset)
            offset += QuestionTrailerSize
        }
        // Read answers until the first TXT one.
        repeat(answerCount) {
            if (offset >= length) return@repeat
            offset = skipName(response, offset)
            if (offset + AnswerFixedPartSize > length) return@repeat
            val type = shortAt(offset)
            val rdLength = shortAt(offset + 8)
            val rdataStart = offset + AnswerFixedPartSize
            if (type == DnsTypeTxt && rdataStart + rdLength <= length) {
                return readCharacterStrings(response, rdataStart, rdLength)
            }
            offset = rdataStart + rdLength
        }
        return null
    }

    private fun readCharacterStrings(response: ByteArray, start: Int, rdLength: Int): List<String> {
        val strings = mutableListOf<String>()
        var offset = start
        val end = start + rdLength
        while (offset < end) {
            val size = response[offset].toInt() and 0xFF
            offset++
            if (offset + size > end) break
            strings.add(response.decodeToString(offset, offset + size))
            offset += size
        }
        return strings
    }

    /** Skips a possibly compressed domain name, returning the next offset. */
    private fun skipName(response: ByteArray, offset: Int): Int {
        var index = offset
        while (index < response.size) {
            val labelSize = response[index].toInt() and 0xFF
            if (labelSize == 0) return index + 1
            if ((labelSize and 0xC0) == 0xC0) return index + 2 // compression pointer
            index += 1 + labelSize
        }
        return index
    }

    private const val DnsTypeTxt = 16
    private const val DnsClassIn = 1
    private const val HeaderSize = 12
    private const val QuestionTrailerSize = 4
    private const val AnswerFixedPartSize = 10 // type(2) class(2) ttl(4) rdlength(2)
}
