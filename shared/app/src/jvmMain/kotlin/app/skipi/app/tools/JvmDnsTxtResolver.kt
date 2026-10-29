// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.RawDnsWire
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

/**
 * Raw DNS TXT client. Over a SOCKS [route] it asks through UDP ASSOCIATE, then DNS-over-TCP, so
 * the probe traverses the tunnel even when the application itself is excluded from direct VPN
 * network binding. [fallbackToDirect] additionally lets a failed proxied query retry over the
 * default path (the established Android behavior); hosts that must not silently change the
 * measured path keep it off.
 */
class JvmDnsTxtResolver(
    private val route: DiagnosticsRoute,
    private val binding: DiagnosticsNetworkBinding = DiagnosticsNetworkBinding.None,
    private val fallbackToDirect: Boolean = false,
    private val timeoutMillis: Int = 4_000,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DnsTxtResolver {
    override suspend fun queryTxt(server: String, domain: String): List<String>? = withContext(ioDispatcher) {
        when (route) {
            is DiagnosticsRoute.Socks ->
                queryViaSocksUdp(server, domain, route)
                    ?: queryViaSocksTcp(server, domain, route)
                    ?: if (fallbackToDirect) queryDirect(server, domain) else null

            DiagnosticsRoute.Direct -> queryDirect(server, domain)
        }
    }

    private fun queryDirect(server: String, domain: String): List<String>? = runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            runCatching { binding.bind(socket) }
            val transactionId = Random.nextInt(0, 0xFFFF)
            val query = RawDnsWire.buildQuery(transactionId, domain)
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), RawDnsWire.DnsPort))
            val buffer = ByteArray(RawDnsWire.MaxResponseBytes)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            RawDnsWire.parseTxtRecords(buffer, packet.length, transactionId)
        }
    }.getOrNull()

    private fun queryViaSocksUdp(server: String, domain: String, socks: DiagnosticsRoute.Socks): List<String>? =
        runCatching {
            Socket().use { tcp ->
                tcp.soTimeout = timeoutMillis
                tcp.connect(InetSocketAddress(socks.host, socks.port), timeoutMillis)
                val out = tcp.getOutputStream()
                val input = tcp.getInputStream()
                if (!negotiate(socks, out, input)) return@use null

                // UDP ASSOCIATE request
                out.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                out.flush()
                val version = input.readUnsigned()
                val reply = input.readUnsigned()
                input.readUnsigned() // RSV
                val addressType = input.readUnsigned()
                if (version != 0x05 || reply != 0x00) return@use null
                val bound: InetAddress = when (addressType) {
                    0x01 -> InetAddress.getByAddress(input.readExactly(4))
                    0x03 -> InetAddress.getByName(input.readExactly(input.readUnsigned()).toString(Charsets.US_ASCII))
                    0x04 -> InetAddress.getByAddress(input.readExactly(16))
                    else -> return@use null
                }
                val boundPort = (input.readUnsigned() shl 8) or input.readUnsigned()
                val relay = if (bound.isAnyLocalAddress) InetAddress.getByName(socks.host) else bound

                DatagramSocket().use { udp ->
                    udp.soTimeout = timeoutMillis
                    val transactionId = Random.nextInt(0, 0xFFFF)
                    val dnsQuery = RawDnsWire.buildQuery(transactionId, domain)
                    val target = InetAddress.getByName(server).address
                    val request = ByteArrayOutputStream().apply {
                        write(byteArrayOf(0x00, 0x00, 0x00)) // RSV, RSV, FRAG
                        write(if (target.size == 4) 0x01 else 0x04)
                        write(target)
                        write((RawDnsWire.DnsPort shr 8) and 0xFF)
                        write(RawDnsWire.DnsPort and 0xFF)
                        write(dnsQuery)
                    }.toByteArray()
                    udp.send(DatagramPacket(request, request.size, relay, boundPort))

                    val buffer = ByteArray(RawDnsWire.MaxResponseBytes)
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    val length = packet.length
                    if (length < 10 || (buffer[2].toInt() and 0xFF) != 0) return@use null
                    val dnsOffset = when (buffer[3].toInt() and 0xFF) {
                        0x01 -> 10
                        0x03 -> 7 + (buffer[4].toInt() and 0xFF)
                        0x04 -> 22
                        else -> return@use null
                    }
                    if (dnsOffset >= length) return@use null
                    val dnsData = buffer.copyOfRange(dnsOffset, length)
                    RawDnsWire.parseTxtRecords(dnsData, dnsData.size, transactionId)
                }
            }
        }.getOrNull()

    private fun queryViaSocksTcp(server: String, domain: String, socks: DiagnosticsRoute.Socks): List<String>? =
        runCatching {
            route.withSocksAuthenticator {
                Socket(socks.javaProxy()).use { socket ->
                    socket.soTimeout = timeoutMillis
                    socket.connect(InetSocketAddress(server, RawDnsWire.DnsPort), timeoutMillis)
                    val out = socket.getOutputStream()
                    val input = socket.getInputStream()
                    val transactionId = Random.nextInt(0, 0xFFFF)
                    val query = RawDnsWire.buildQuery(transactionId, domain)
                    out.write((query.size shr 8) and 0xFF)
                    out.write(query.size and 0xFF)
                    out.write(query)
                    out.flush()
                    val responseLength = (input.readUnsigned() shl 8) or input.readUnsigned()
                    if (responseLength <= 0 || responseLength > RawDnsWire.MaxResponseBytes) return@use null
                    val response = input.readExactly(responseLength)
                    RawDnsWire.parseTxtRecords(response, response.size, transactionId)
                }
            }
        }.getOrNull()

    /** SOCKS5 method negotiation (and username/password sub-negotiation when configured). */
    private fun negotiate(socks: DiagnosticsRoute.Socks, out: java.io.OutputStream, input: InputStream): Boolean {
        if (socks.username.isBlank()) {
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            return input.readUnsigned() == 0x05 && input.readUnsigned() == 0x00
        }
        out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        out.flush()
        if (input.readUnsigned() != 0x05) return false
        when (input.readUnsigned()) {
            0x02 -> {
                val user = socks.username.toByteArray(Charsets.UTF_8)
                val pass = socks.password.toByteArray(Charsets.UTF_8)
                out.write(byteArrayOf(0x01, user.size.toByte()))
                out.write(user)
                out.write(byteArrayOf(pass.size.toByte()))
                out.write(pass)
                out.flush()
                input.readUnsigned() // version
                return input.readUnsigned() == 0x00
            }

            0x00 -> return true
            else -> return false
        }
    }

    private fun InputStream.readUnsigned(): Int {
        val value = read()
        if (value == -1) throw EOFException("Unexpected EOF")
        return value and 0xFF
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(buffer, offset, count - offset)
            if (read == -1) throw EOFException("Unexpected EOF")
            offset += read
        }
        return buffer
    }
}
