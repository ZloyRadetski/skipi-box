// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import android.net.Network
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import java.io.InputStream
import java.net.Authenticator
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.Socket
import kotlin.random.Random

/**
 * Minimal raw DNS client: sends a single UDP TXT query to [server]:53 and
 * returns the character strings of the first TXT answer record. Used to ask
 * Google's echo record which resolver egress and EDNS Client Subnet our
 * queries expose. No external DNS library is required.
 *
 * When [proxyOptions] is provided, queries are sent through the local SOCKS5
 * proxy (first via UDP ASSOCIATE, falling back to DNS-over-TCP). This ensures
 * probes traverse the active VPN tunnel properly even when the app is excluded
 * from direct VPN network binding.
 */
internal object RawDnsClient {

    /** Returns the TXT strings of the answer, or null on any failure/timeout. */
    fun queryTxt(
        server: String,
        domain: String,
        timeoutMillis: Int = 4_000,
        network: Network? = null,
        proxyOptions: LocalProxyOptions? = null,
    ): List<String>? {
        if (proxyOptions != null) {
            queryTxtViaSocksUdp(server, domain, timeoutMillis, proxyOptions)?.let { return it }
            queryTxtViaSocksTcp(server, domain, timeoutMillis, proxyOptions)?.let { return it }
        }
        return queryTxtDirect(server, domain, timeoutMillis, network)
    }

    private fun queryTxtDirect(
        server: String,
        domain: String,
        timeoutMillis: Int,
        network: Network?,
    ): List<String>? {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMillis
                if (network != null) {
                    runCatching { network.bindSocket(socket) }
                }
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

    private fun queryTxtViaSocksUdp(
        server: String,
        domain: String,
        timeoutMillis: Int,
        proxyOptions: LocalProxyOptions,
    ): List<String>? {
        return runCatching {
            val proxyHost = if (proxyOptions.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
                LocalProxyLoopbackAddress
            } else {
                proxyOptions.listenAddress
            }
            Socket().use { tcpSocket ->
                tcpSocket.soTimeout = timeoutMillis
                tcpSocket.connect(InetSocketAddress(proxyHost, proxyOptions.port), timeoutMillis)
                val out = tcpSocket.getOutputStream()
                val input = tcpSocket.getInputStream()

                // SOCKS5 Auth negotiation
                if (proxyOptions.username.isNotBlank()) {
                    out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
                    out.flush()
                    val authVer = input.readByteOrThrow()
                    val authMethod = input.readByteOrThrow()
                    if (authVer != 0x05) return null
                    if (authMethod == 0x02) {
                        val userBytes = proxyOptions.username.toByteArray(Charsets.UTF_8)
                        val passBytes = proxyOptions.password.toByteArray(Charsets.UTF_8)
                        out.write(byteArrayOf(0x01, userBytes.size.toByte()))
                        out.write(userBytes)
                        out.write(byteArrayOf(passBytes.size.toByte()))
                        out.write(passBytes)
                        out.flush()
                        val ver = input.readByteOrThrow()
                        val status = input.readByteOrThrow()
                        if (status != 0x00) return null
                    } else if (authMethod != 0x00) {
                        return null
                    }
                } else {
                    out.write(byteArrayOf(0x05, 0x01, 0x00))
                    out.flush()
                    val ver = input.readByteOrThrow()
                    val method = input.readByteOrThrow()
                    if (ver != 0x05 || method != 0x00) return null
                }

                // SOCKS5 UDP ASSOCIATE request
                out.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                out.flush()

                val respVer = input.readByteOrThrow()
                val respRep = input.readByteOrThrow()
                val respRsv = input.readByteOrThrow()
                val respAtyp = input.readByteOrThrow()
                if (respVer != 0x05 || respRep != 0x00) return null

                val bndAddr: InetAddress = when (respAtyp) {
                    0x01 -> {
                        val ip = ByteArray(4)
                        input.readFully(ip)
                        InetAddress.getByAddress(ip)
                    }
                    0x03 -> {
                        val len = input.readByteOrThrow()
                        val domainBytes = ByteArray(len)
                        input.readFully(domainBytes)
                        InetAddress.getByName(String(domainBytes, Charsets.US_ASCII))
                    }
                    0x04 -> {
                        val ip = ByteArray(16)
                        input.readFully(ip)
                        InetAddress.getByAddress(ip)
                    }
                    else -> return null
                }
                val bndPort = (input.readByteOrThrow() shl 8) or input.readByteOrThrow()
                val relayAddress = if (bndAddr.isAnyLocalAddress || bndAddr.hostAddress == "0.0.0.0") {
                    InetAddress.getByName(proxyHost)
                } else {
                    bndAddr
                }

                DatagramSocket().use { udpSocket ->
                    udpSocket.soTimeout = timeoutMillis
                    val transactionId = Random.nextInt(0, 0xFFFF)
                    val dnsQuery = buildQuery(transactionId, domain)

                    val targetAddr = InetAddress.getByName(server)
                    val targetIpBytes = targetAddr.address
                    val socksUdpHeader = java.io.ByteArrayOutputStream()
                    socksUdpHeader.write(0x00) // RSV
                    socksUdpHeader.write(0x00) // RSV
                    socksUdpHeader.write(0x00) // FRAG
                    if (targetIpBytes.size == 4) {
                        socksUdpHeader.write(0x01) // IPv4
                        socksUdpHeader.write(targetIpBytes)
                    } else {
                        socksUdpHeader.write(0x04) // IPv6
                        socksUdpHeader.write(targetIpBytes)
                    }
                    socksUdpHeader.write((DnsPort shr 8) and 0xFF)
                    socksUdpHeader.write(DnsPort and 0xFF)
                    socksUdpHeader.write(dnsQuery)

                    val sendBytes = socksUdpHeader.toByteArray()
                    udpSocket.send(DatagramPacket(sendBytes, sendBytes.size, relayAddress, bndPort))

                    val recvBuffer = ByteArray(MaxResponseBytes)
                    val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                    udpSocket.receive(recvPacket)

                    val len = recvPacket.length
                    if (len < 10) return null
                    val frag = recvBuffer[2].toInt() and 0xFF
                    if (frag != 0) return null
                    val atyp = recvBuffer[3].toInt() and 0xFF
                    val dnsOffset = when (atyp) {
                        0x01 -> 10
                        0x03 -> 7 + (recvBuffer[4].toInt() and 0xFF)
                        0x04 -> 22
                        else -> return null
                    }
                    if (dnsOffset >= len) return null
                    val dnsData = recvBuffer.copyOfRange(dnsOffset, len)
                    parseTxtRecords(dnsData, dnsData.size, transactionId)
                }
            }
        }.getOrNull()
    }

    private fun queryTxtViaSocksTcp(
        server: String,
        domain: String,
        timeoutMillis: Int,
        proxyOptions: LocalProxyOptions,
    ): List<String>? {
        return runCatching {
            val proxyHost = if (proxyOptions.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
                LocalProxyLoopbackAddress
            } else {
                proxyOptions.listenAddress
            }
            val javaProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyOptions.port))
            proxyOptions.withProxyAuthenticator {
                Socket(javaProxy).use { socket ->
                    socket.soTimeout = timeoutMillis
                    socket.connect(InetSocketAddress(server, DnsPort), timeoutMillis)
                    val out = socket.getOutputStream()
                    val input = socket.getInputStream()

                    val transactionId = Random.nextInt(0, 0xFFFF)
                    val dnsQuery = buildQuery(transactionId, domain)

                    out.write((dnsQuery.size shr 8) and 0xFF)
                    out.write(dnsQuery.size and 0xFF)
                    out.write(dnsQuery)
                    out.flush()

                    val lenHigh = input.readByteOrThrow()
                    val lenLow = input.readByteOrThrow()
                    val responseLen = (lenHigh shl 8) or lenLow
                    if (responseLen <= 0 || responseLen > MaxResponseBytes) return null

                    val buffer = ByteArray(responseLen)
                    input.readFully(buffer)
                    parseTxtRecords(buffer, buffer.size, transactionId)
                }
            }
        }.getOrNull()
    }

    private fun InputStream.readByteOrThrow(): Int {
        val b = read()
        if (b == -1) throw java.io.EOFException("Unexpected EOF")
        return b and 0xFF
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count == -1) throw java.io.EOFException("Unexpected EOF")
            offset += count
        }
    }

    internal fun buildQuery(transactionId: Int, domain: String): ByteArray {
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

    internal fun parseTxtRecords(
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

internal inline fun <T> LocalProxyOptions?.withProxyAuthenticator(block: () -> T): T {
    if (this == null || username.isBlank()) return block()
    synchronized(LocalProxyAuthenticatorLock) {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestingPort != port) return null
                return PasswordAuthentication(username, password.toCharArray())
            }
        })
        return try {
            block()
        } finally {
            Authenticator.setDefault(null)
        }
    }
}

private val LocalProxyAuthenticatorLock = Any()

