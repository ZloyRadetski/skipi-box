// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import com.sun.net.httpserver.HttpServer
import features.tools.dnsleak.RawDnsWire
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.HttpURLConnection
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmDiagnosticsTransportsTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() {
        closeables.forEach { runCatching { it.close() } }
    }

    private fun httpServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { server ->
            server.createContext("/") { exchange -> handler(exchange); exchange.close() }
            server.start()
            closeables += AutoCloseable { server.stop(0) }
        }

    private fun HttpServer.url(path: String) = "http://127.0.0.1:${address.port}$path"

    @Test
    fun httpClientReturnsBodyOf200AndSendsAcceptHeader() = runBlocking {
        var accept: String? = null
        val server = httpServer { exchange ->
            accept = exchange.requestHeaders.getFirst("Accept")
            val body = "203.0.113.9".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
        }

        val body = JvmDiagnosticsHttpClient(DiagnosticsRoute.Direct).getText(server.url("/ip"), "text/plain")

        assertEquals("203.0.113.9", body)
        assertEquals("text/plain", accept)
    }

    @Test
    fun httpClientReportsNon200AndUnreachableHostsAsNull() = runBlocking {
        val server = httpServer { exchange -> exchange.sendResponseHeaders(404, -1) }
        val failures = mutableListOf<String>()
        val client = JvmDiagnosticsHttpClient(DiagnosticsRoute.Direct, onFailure = { url, _ -> failures += url })

        assertNull(client.getText(server.url("/missing"), "*/*"))
        val dead = ServerSocket(0).use { it.localPort }
        assertNull(client.getText("http://127.0.0.1:$dead/", "*/*"))
        assertEquals(1, failures.size)
    }

    @Test
    fun speedTransportCountsDownloadedAndUploadedBytesAndMeasuresRoundTrip() = runBlocking {
        var uploaded = 0L
        val server = httpServer { exchange ->
            if (exchange.requestMethod == "POST") {
                uploaded = exchange.requestBody.readAllBytes().size.toLong()
                exchange.sendResponseHeaders(200, -1)
            } else {
                val payload = ByteArray(300_000)
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.write(payload)
            }
        }
        val transport = JvmSpeedTestTransport(
            SpeedTestConnectionFactory { url, configure ->
                (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    configure(this)
                    connect()
                }
            },
        )

        var downloaded = 0L
        transport.download(server.url("/down")) { downloaded += it }
        var sent = 0L
        transport.upload(server.url("/up"), 500_000L) { sent += it }
        val roundTrip = transport.roundTripMillis(server.url("/down"))

        assertEquals(300_000L, downloaded)
        assertEquals(500_000L, sent)
        assertEquals(500_000L, uploaded)
        assertTrue(roundTrip > 0.0)
    }

    /** Minimal SOCKS5 server with UDP ASSOCIATE; answers every DNS query with fixed TXT strings. */
    private class FakeSocks5Dns(val requireAuth: Boolean, val txt: List<String>) : AutoCloseable {
        private val tcp = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        private val udp = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val port get() = tcp.localPort
        @Volatile var authenticatedAs: String? = null

        init {
            thread(isDaemon = true) {
                runCatching {
                    val packet = DatagramPacket(ByteArray(2048), 2048)
                    udp.receive(packet)
                    val data = packet.data
                    val query = data.copyOfRange(10, packet.length)
                    val response = dnsResponse(query)
                    val header = byteArrayOf(0, 0, 0, 1, 8, 8, 8, 8, 0, 53)
                    val out = header + response
                    udp.send(DatagramPacket(out, out.size, packet.address, packet.port))
                }
            }
            thread(isDaemon = true) {
                runCatching {
                    tcp.accept().use { socket ->
                        val input = socket.getInputStream()
                        val out = socket.getOutputStream()
                        input.readExactly(2) // version, method count
                        val methods = input.readExactly(input.available().coerceAtLeast(1))
                        if (requireAuth) {
                            check(methods.contains(0x02))
                            out.write(byteArrayOf(0x05, 0x02))
                            input.readExactly(1)
                            val user = input.readExactly(input.read())
                            val pass = input.readExactly(input.read())
                            authenticatedAs = user.decodeToString() + ":" + pass.decodeToString()
                            out.write(byteArrayOf(0x01, 0x00))
                        } else {
                            out.write(byteArrayOf(0x05, 0x00))
                        }
                        input.readExactly(10)
                        val relayPort = udp.localPort
                        out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, (relayPort shr 8).toByte(), relayPort.toByte()))
                        out.flush()
                        Thread.sleep(1_500)
                    }
                }
            }
        }

        private fun dnsResponse(query: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(query, 0, 2)
            out.write(byteArrayOf(0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0))
            out.write(query, 12, query.size - 12) // question
            out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, 16, 0, 1, 0, 0, 0, 60))
            val rdata = ByteArrayOutputStream()
            txt.forEach { rdata.write(it.length); rdata.write(it.toByteArray()) }
            out.write(rdata.size() shr 8); out.write(rdata.size() and 0xFF)
            out.write(rdata.toByteArray())
            return out.toByteArray()
        }

        private fun InputStream.readExactly(count: Int): ByteArray {
            val buffer = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val n = read(buffer, offset, count - offset)
                if (n < 0) error("eof")
                offset += n
            }
            return buffer
        }

        override fun close() {
            runCatching { tcp.close() }
            runCatching { udp.close() }
        }
    }

    @Test
    fun dnsResolverQueriesThroughSocksUdpAssociate() = runBlocking {
        val socks = FakeSocks5Dns(requireAuth = false, txt = listOf("203.0.113.9", "edns0-client-subnet 198.51.100.0/24"))
        closeables += socks
        val resolver = JvmDnsTxtResolver(DiagnosticsRoute.Socks("127.0.0.1", socks.port), timeoutMillis = 2_000)

        val records = resolver.queryTxt("8.8.8.8", "o-o.myaddr.l.google.com")

        assertEquals(listOf("203.0.113.9", "edns0-client-subnet 198.51.100.0/24"), records)
    }

    @Test
    fun dnsResolverAuthenticatesWithSocksCredentials() = runBlocking {
        val socks = FakeSocks5Dns(requireAuth = true, txt = listOf("203.0.113.9"))
        closeables += socks
        val resolver = JvmDnsTxtResolver(
            DiagnosticsRoute.Socks("127.0.0.1", socks.port, "alice", "secret"),
            timeoutMillis = 2_000,
        )

        val records = resolver.queryTxt("8.8.8.8", "o-o.myaddr.l.google.com")

        assertEquals(listOf("203.0.113.9"), records)
        assertEquals("alice:secret", socks.authenticatedAs)
    }

    @Test
    fun dnsResolverDoesNotFallBackToDirectUnlessAsked() = runBlocking {
        val deadPort = ServerSocket(0).use { it.localPort }
        val resolver = JvmDnsTxtResolver(DiagnosticsRoute.Socks("127.0.0.1", deadPort), timeoutMillis = 300)

        assertNull(resolver.queryTxt("127.0.0.1", "o-o.myaddr.l.google.com"))
        assertEquals(53, RawDnsWire.DnsPort)
    }
}
