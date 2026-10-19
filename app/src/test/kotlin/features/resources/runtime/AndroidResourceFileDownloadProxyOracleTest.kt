// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.app.Application
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Loopback source oracle for the current Android proxy-first resource downloader. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = Application::class,
    shadows = [RealOsRenameShadow::class],
)
class AndroidResourceFileDownloadProxyOracleTest {
    @Before
    fun resetCancellationState() {
        AndroidResourceFileDownloadCancellation.begin()
    }

    @Test
    fun exhaustedProxiedSocketFailuresFallBackToOneDirectGet() = withTempDirectory { directory ->
        val directRequests = AtomicInteger()
        val body = "direct resource bytes".encodeToByteArray()
        val target = File(directory, "geoip.dat").apply { writeText("previous") }

        withHttpServer { direct ->
            direct.createContext("/resource") { exchange ->
                directRequests.incrementAndGet()
                exchange.respond(200, body)
            }

            LoopbackSocksServer(Mode.REJECT_METHOD).use { socks ->
                AndroidResourceFileDownloader(context = null).download(
                    url = "http://127.0.0.1:" + direct.address.port + "/resource",
                    target = target,
                    proxy = socks.proxy(),
                )

                assertTrue(socks.sessions.isNotEmpty())
                assertTrue(socks.sessions.all { it.selectedMethod == 255 })
            }
        }

        assertEquals(1, directRequests.get())
        assertEquals(body.toList(), target.readBytes().toList())
    }

    @Test
    fun proxiedHttpFailureDoesNotFallBackToDirect() = withTempDirectory { directory ->
        val directRequests = AtomicInteger()
        val target = File(directory, "geosite.dat").apply { writeText("previous") }
        var error: Throwable? = null

        withHttpServer { direct ->
            direct.createContext("/resource") { exchange ->
                directRequests.incrementAndGet()
                exchange.respond(200, "must not be requested".encodeToByteArray())
            }

            LoopbackSocksServer(Mode.HTTP_500).use { socks ->
                error = runCatching {
                    AndroidResourceFileDownloader(context = null).download(
                        url = "http://127.0.0.1:" + direct.address.port + "/resource",
                        target = target,
                        proxy = socks.proxy(),
                    )
                }.exceptionOrNull()

                assertTrue(socks.sessions.isNotEmpty())
                assertTrue(socks.sessions.all { it.selectedMethod == 0 })
            }
        }

        assertTrue(error is IllegalStateException)
        assertEquals("HTTP 500", error?.message)
        assertEquals(0, directRequests.get())
        assertEquals("previous", target.readText())
    }

    @Test
    fun socksCredentialsRemainAvailableAcrossProxiedRetriesBeforeDirectFallback() = withTempDirectory { directory ->
        val previousAuthenticator = currentAuthenticator()
        val directRequests = AtomicInteger()
        val target = File(directory, "geoip.dat").apply { writeText("previous") }
        val body = "direct after authenticated proxy failures".encodeToByteArray()
        val expectedCredentials = SocksCredentials("proxy-test-user", "proxy-test-password")

        try {
            withHttpServer { direct ->
                direct.createContext("/resource") { exchange ->
                    directRequests.incrementAndGet()
                    exchange.respond(200, body)
                }

                LoopbackSocksServer(Mode.AUTH_CONNECT_FAILURE).use { socks ->
                    AndroidResourceFileDownloader(context = null).download(
                        url = "http://127.0.0.1:" + direct.address.port + "/resource",
                        target = target,
                        proxy = socks.proxy(
                            username = expectedCredentials.username,
                            password = expectedCredentials.password,
                        ),
                    )

                    assertTrue(socks.sessions.isNotEmpty())
                    assertTrue(socks.sessions.all { it.selectedMethod == 2 })
                    assertTrue(socks.sessions.all { it.credentials == expectedCredentials })
                }
            }
        } finally {
            Authenticator.setDefault(previousAuthenticator)
        }

        assertEquals(1, directRequests.get())
        assertEquals(body.toList(), target.readBytes().toList())
    }

    @Test
    fun blankUsernameKeepsProxyRouteAndDoesNotReplaceInstalledAuthenticator() = withTempDirectory { directory ->
        val previousAuthenticator = currentAuthenticator()
        val sentinelAuthenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? = null
        }
        val directRequests = AtomicInteger()
        val target = File(directory, "geosite.dat").apply { writeText("previous") }

        Authenticator.setDefault(sentinelAuthenticator)
        try {
            withHttpServer { direct ->
                direct.createContext("/resource") { exchange ->
                    directRequests.incrementAndGet()
                    exchange.respond(200, "direct route".encodeToByteArray())
                }

                LoopbackSocksServer(Mode.HTTP_200).use { socks ->
                    AndroidResourceFileDownloader(context = null).download(
                        url = "http://127.0.0.1:" + direct.address.port + "/resource",
                        target = target,
                        proxy = socks.proxy(username = " \t", password = "unused"),
                    )

                    assertTrue(socks.sessions.isNotEmpty())
                    assertTrue(socks.sessions.all { it.selectedMethod == 0 })
                    assertTrue(socks.sessions.all { it.authenticatorAtGreeting === sentinelAuthenticator })
                }
            }
        } finally {
            Authenticator.setDefault(previousAuthenticator)
        }

        assertEquals(0, directRequests.get())
        assertEquals("proxied response", target.readText())
    }
}

private enum class Mode {
    REJECT_METHOD,
    HTTP_500,
    AUTH_CONNECT_FAILURE,
    HTTP_200,
}

private data class SocksCredentials(
    val username: String,
    val password: String,
)

private data class SocksSession(
    val offeredMethods: List<Int>,
    val selectedMethod: Int,
    val credentials: SocksCredentials?,
    val authenticatorAtGreeting: Authenticator?,
)

private class LoopbackSocksServer(
    private val mode: Mode,
) : AutoCloseable {
    private val listener = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1")).apply {
        soTimeout = 200
    }
    private val closed = AtomicBoolean(false)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val recordedSessions = Collections.synchronizedList(mutableListOf<SocksSession>())
    private val workerFailure = AtomicReference<Throwable?>(null)
    private val acceptThread = Thread(::acceptLoop, "skipi-resource-socks-oracle").apply {
        isDaemon = true
        start()
    }

    val port: Int get() = listener.localPort

    val sessions: List<SocksSession>
        get() = synchronized(recordedSessions) { recordedSessions.toList() }

    fun proxy(
        username: String = "",
        password: String = "",
    ) = AndroidResourceFileDownloadProxy(
        host = "127.0.0.1",
        port = port,
        username = username,
        password = password,
    )

    private fun acceptLoop() {
        while (!closed.get()) {
            val socket = try {
                listener.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: SocketException) {
                return
            }
            activeSockets += socket
            try {
                socket.use { accepted ->
                    accepted.soTimeout = 4_000
                    serve(accepted)
                }
            } catch (error: Throwable) {
                if (!closed.get()) workerFailure.compareAndSet(null, error)
            } finally {
                activeSockets -= socket
            }
        }
    }

    private fun serve(socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        check(input.read() == 5)
        val methodCount = input.read()
        check(methodCount >= 0)
        val methods = List(methodCount) { input.readRequiredByte() }
        val authenticator = currentAuthenticator()
        val selectedMethod = when (mode) {
            Mode.REJECT_METHOD -> 255
            Mode.AUTH_CONNECT_FAILURE -> if (2 in methods) 2 else 255
            Mode.HTTP_500, Mode.HTTP_200 -> if (0 in methods) 0 else 255
        }
        output.write(byteArrayOf(5, selectedMethod.toByte()))
        output.flush()

        if (selectedMethod == 255) {
            recordedSessions += SocksSession(methods, selectedMethod, null, authenticator)
            return
        }

        val credentials = if (selectedMethod == 2) {
            check(input.read() == 1)
            val usernameLength = input.readRequiredByte()
            val username = input.readRequiredBytes(usernameLength).decodeToString()
            val passwordLength = input.readRequiredByte()
            val password = input.readRequiredBytes(passwordLength).decodeToString()
            output.write(byteArrayOf(1, 0))
            output.flush()
            SocksCredentials(username, password)
        } else {
            null
        }

        input.readConnectRequest()
        recordedSessions += SocksSession(methods, selectedMethod, credentials, authenticator)

        if (mode == Mode.AUTH_CONNECT_FAILURE) {
            output.write(socksConnectReply(replyCode = 5))
            output.flush()
            return
        }

        output.write(socksConnectReply(replyCode = 0))
        output.flush()
        input.readHttpHeaders()
        when (mode) {
            Mode.HTTP_500 -> output.write(
                "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .encodeToByteArray(),
            )
            Mode.HTTP_200 -> output.write(
                "HTTP/1.1 200 OK\r\nContent-Length: 16\r\nConnection: close\r\n\r\nproxied response"
                    .encodeToByteArray(),
            )
            Mode.REJECT_METHOD, Mode.AUTH_CONNECT_FAILURE -> error("Unexpected SOCKS success mode")
        }
        output.flush()
    }

    override fun close() {
        closed.set(true)
        runCatching { listener.close() }
        activeSockets.forEach { runCatching { it.close() } }
        acceptThread.join(5_000)
        workerFailure.get()?.let { throw AssertionError("SOCKS test fixture failed", it) }
    }

    private fun socksConnectReply(replyCode: Int): ByteArray =
        byteArrayOf(5, replyCode.toByte(), 0, 1, 127, 0, 0, 1, 0, 0)
}

private fun InputStream.readRequiredByte(): Int {
    val value = read()
    if (value < 0) throw EOFException("Unexpected end of SOCKS request")
    return value
}

private fun currentAuthenticator(): Authenticator? =
    Authenticator::class.java.getMethod("getDefault").invoke(null) as Authenticator?

private fun InputStream.readRequiredBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val readCount = read(bytes, offset, count - offset)
        if (readCount < 0) throw EOFException("Unexpected end of SOCKS request")
        offset += readCount
    }
    return bytes
}

private fun InputStream.readConnectRequest() {
    check(readRequiredByte() == 5)
    check(readRequiredByte() == 1)
    readRequiredByte()
    when (readRequiredByte()) {
        1 -> readRequiredBytes(4)
        3 -> readRequiredBytes(readRequiredByte())
        4 -> readRequiredBytes(16)
        else -> error("Unknown SOCKS address type")
    }
    readRequiredBytes(2)
}

private fun InputStream.readHttpHeaders() {
    while (readHttpLine().isNotEmpty()) Unit
}

private fun InputStream.readHttpLine(): String {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val value = read()
        if (value < 0) throw EOFException("Unexpected end of HTTP request")
        if (value == '\n'.code) {
            val line = bytes.toByteArray().decodeToString()
            return line.removeSuffix("\r")
        }
        bytes.write(value)
    }
}

private inline fun <T> withHttpServer(block: (HttpServer) -> T): T {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.start()
    try {
        return block(server)
    } finally {
        server.stop(0)
    }
}

private inline fun <T> withTempDirectory(block: (File) -> T): T {
    val directory = Files.createTempDirectory("android-resource-proxy-oracle-").toFile()
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

private fun HttpExchange.respond(code: Int, body: ByteArray) {
    sendResponseHeaders(code, body.size.toLong())
    responseBody.use { it.write(body) }
}
