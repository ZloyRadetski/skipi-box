// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import android.net.Network
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

/**
 * Minimal raw DNS client: sends a single UDP TXT query to [server]:53 and
 * returns the character strings of the first TXT answer record. Used to ask
 * Google's echo record which resolver egress and EDNS Client Subnet our
 * queries expose. No external DNS library is required.
 *
 * When [network] is provided the socket is bound to it, forcing the query
 * through that network (the VPN tunnel) instead of the default one - the app
 * itself is excluded from its own VPN, so binding is required to probe the
 * same path other apps' DNS traffic takes.
 */
internal object RawDnsClient {

    /** Returns the TXT strings of the answer, or null on any failure/timeout. */
    fun queryTxt(
        server: String,
        domain: String,
        timeoutMillis: Int = 4_000,
        network: Network? = null,
    ): List<String>? {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMillis
                network?.bindSocket(socket)
                val transactionId = Random.nextInt(0, 0xFFFF)
                val query = buildQuery(transactionId, domain)
                val address = InetAddress.getByName(server)
                socket.send(DatagramPacket(query, query.size, address, DnsPort))
                val buffer = ByteArray(MaxResponseBytes)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                parseTxtRecords(buffer, packet.length, transactionId)
            }
        }.getOrNull()
    }

    private fun buildQuery(transactionId: Int, domain: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun writeShort(value: Int) {
            out.write((value shr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        writeShort(transactionId)
        writeShort(0x0100) // flags: recursion desired
        writeShort(1) // qdcount
        writeShort(0) // ancount
        writeShort(0) // nscount
        writeShort(0) // arcount
        domain.split('.').filter { it.isNotBlank() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0) // root label terminator
        writeShort(DnsTypeTxt)
        writeShort(DnsClassIn)
        return out.toByteArray()
    }

    private fun parseTxtRecords(
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
            strings.add(String(response, offset, size, Charsets.UTF_8))
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

    private const val DnsPort = 53
    private const val DnsTypeTxt = 16
    private const val DnsClassIn = 1
    private const val HeaderSize = 12
    private const val QuestionTrailerSize = 4
    private const val AnswerFixedPartSize = 10 // type(2) class(2) ttl(4) rdlength(2)
    private const val MaxResponseBytes = 2048
}
