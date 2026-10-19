// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileDownloadCancelledException
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.runtime.ResourceFileDownloadCancellation
import com.sun.net.httpserver.HttpServer
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.ServerSocket
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopResourceFileDownloaderTest {

    @Test
    fun directDownloadUsesGetUserAgentAndForwardsKnownLengthProgressThroughPublisher() = withFreshCancellation {
        withHttpServer { server ->
            val body = "resource-data".encodeToByteArray()
            val observed = CopyOnWriteArrayList<Pair<String, String?>>()
            server.createContext("/resource") { exchange ->
                observed += exchange.requestMethod to exchange.requestHeaders.getFirst("User-Agent")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            val publications = mutableListOf<Pair<File, ByteArray>>()
            val downloader = DesktopResourceFileDownloader { target, write ->
                val output = ByteArrayOutputStream()
                write(output)
                publications += target to output.toByteArray()
            }
            val defaultProgress = mutableListOf<Pair<Long, Long>>()
            val explicitProgress = mutableListOf<Pair<Long, Long>>()
            val url = "http://127.0.0.1:${server.address.port}/resource"

            downloader.download(url, File("default.dat"), onProgress = { downloaded, total ->
                defaultProgress += downloaded to total
            })
            downloader.download(
                url,
                File("explicit.dat"),
                userAgent = "Profile Agent/9",
                onProgress = { downloaded, total -> explicitProgress += downloaded to total },
            )

            assertEquals(
                listOf<Pair<String, String?>>("GET" to "SKIPI/1.0", "GET" to "Profile Agent/9"),
                observed.toList(),
            )
            assertEquals(listOf(File("default.dat"), File("explicit.dat")), publications.map { it.first })
            publications.forEach { (_, published) -> assertContentEquals(body, published) }
            assertProgressSequence(defaultProgress, body.size.toLong())
            assertProgressSequence(explicitProgress, body.size.toLong())
        }
    }

    @Test
    fun strictProfileWriterRejectsEmptyBodyAndPreservesTargetAndSibling() = withFreshCancellation {
        withXrayRoot { root ->
            withHttpServer { server ->
                server.createContext("/empty") { exchange ->
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                }
                val validBody = "valid-resource".encodeToByteArray()
                server.createContext("/valid") { exchange ->
                    exchange.sendResponseHeaders(200, validBody.size.toLong())
                    exchange.responseBody.use { it.write(validBody) }
                }
                val profileFiles = DesktopProfileResourceFiles(root)
                val target = profileFiles.snapshot(profile(40)).file(ResourceFileKind.GeoIp).apply {
                    parentFile.mkdirs()
                    writeText("old-target")
                }
                val sibling = File(target.parentFile, "sibling.keep").apply { writeText("sibling-bytes") }
                val downloader = profileFiles.createDownloader()

                downloader.download("http://127.0.0.1:${server.address.port}/valid", target)
                assertContentEquals(validBody, target.readBytes())

                assertFailsWith<IllegalStateException> {
                    downloader.download(
                        "http://127.0.0.1:${server.address.port}/empty",
                        target,
                    )
                }

                assertContentEquals(validBody, target.readBytes())
                assertEquals("sibling-bytes", sibling.readText())
                assertFalse(
                    target.parentFile.listFiles().orEmpty().any {
                        it.name.startsWith("${target.name}.") && it.name.endsWith(".tmp")
                    },
                )
            }
        }
    }

    @Test
    fun authenticatedSocksDownloadRestoresThePreviousGlobalAuthenticator() = withFreshCancellation {
        val originalAuthenticator = Authenticator.getDefault()
        val previousAuthenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? = null
        }
        Authenticator.setDefault(previousAuthenticator)
        try {
            SocksReplyServer().use { proxyServer ->
                val published = ByteArrayOutputStream()
                val downloader = DesktopResourceFileDownloader { _, write -> write(published) }
                val proxy = DesktopSubscriptionSocksProxy(
                    host = "127.0.0.1",
                    port = proxyServer.port,
                    username = "resource-user",
                    password = "resource-secret",
                )

                downloader.download(
                    url = "http://127.0.0.1:1/resource",
                    target = File("socks.dat"),
                    proxy = proxy,
                )
                proxyServer.awaitFinished()

                assertEquals("resource-user", proxyServer.username.get())
                assertEquals("resource-secret", proxyServer.password.get())
                assertContentEquals("socks-body".encodeToByteArray(), published.toByteArray())
                assertSame(previousAuthenticator, Authenticator.getDefault())
            }
        } finally {
            Authenticator.setDefault(originalAuthenticator)
        }
    }

    @Test
    fun ioFailureThroughSocksFallsBackToOneDirectRequest() = withFreshCancellation {
        withHttpServer { server ->
            val directRequests = AtomicInteger()
            val body = "direct-after-proxy-failure".encodeToByteArray()
            server.createContext("/resource") { exchange ->
                directRequests.incrementAndGet()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            RejectingSocksServer().use { proxyServer ->
                val published = ByteArrayOutputStream()
                val downloader = DesktopResourceFileDownloader { _, write -> write(published) }
                val proxy = DesktopSubscriptionSocksProxy(host = "127.0.0.1", port = proxyServer.port)

                downloader.download(
                    url = "http://127.0.0.1:${server.address.port}/resource",
                    target = File("fallback.dat"),
                    proxy = proxy,
                )
                proxyServer.stopAndAwait()

                assertTrue(proxyServer.connectionCount.get() > 0, "The proxy path should be attempted")
                assertEquals(1, directRequests.get())
                assertContentEquals(body, published.toByteArray())
            }
        }
    }

    @Test
    fun preCancelledDownloadMakesNoHttpRequestAndDoesNotPublish() = withFreshCancellation {
        withXrayRoot { root ->
            withHttpServer { server ->
                val requests = AtomicInteger()
                server.createContext("/resource") { exchange ->
                    requests.incrementAndGet()
                    exchange.sendResponseHeaders(200, 4)
                    exchange.responseBody.use { it.write("data".encodeToByteArray()) }
                }
                val target = File(root, "cancelled.dat").apply { writeText("old-target") }
                val publications = AtomicInteger()
                val downloader = DesktopResourceFileDownloader { _, write ->
                    publications.incrementAndGet()
                    write(ByteArrayOutputStream())
                }
                ResourceFileDownloadCancellation.cancel()

                assertFailsWith<ResourceFileDownloadCancelledException> {
                    downloader.download("http://127.0.0.1:${server.address.port}/resource", target)
                }

                assertEquals(0, requests.get())
                assertEquals(0, publications.get())
                assertEquals("old-target", target.readText())
            }
        }
    }

    @Test
    fun cancellingWhileHttpResponseHeadersAreBlockedDisconnectsAndKeepsExistingTarget() = withFreshCancellation {
        withXrayRoot { root ->
            withHttpServer { server ->
                val requestReceived = CountDownLatch(1)
                val releaseResponse = CountDownLatch(1)
                val handlerFinished = CountDownLatch(1)
                server.createContext("/blocked") { exchange ->
                    requestReceived.countDown()
                    try {
                        releaseResponse.await()
                        val body = "replacement".encodeToByteArray()
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    } catch (_: java.io.IOException) {
                        // The client may disconnect after cancellation while the fixture is released.
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally {
                        exchange.close()
                        handlerFinished.countDown()
                    }
                }

                val target = DesktopProfileResourceFiles(root).snapshot(profile(41))
                    .file(ResourceFileKind.GeoIp)
                    .apply {
                        parentFile.mkdirs()
                        writeText("old-target")
                    }
                val downloader = DesktopProfileResourceFiles(root).createDownloader()
                val downloadExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("resource-download"))
                var downloadFinished = false
                try {
                    val future = downloadExecutor.submit {
                        downloader.download("http://127.0.0.1:${server.address.port}/blocked", target)
                    }
                    assertTrue(requestReceived.await(3, SECONDS), "The local server should receive the request")
                    ResourceFileDownloadCancellation.cancel()

                    val failure = assertFailsWith<ExecutionException> { future.get(3, SECONDS) }
                    assertTrue(failure.cause is ResourceFileDownloadCancelledException)
                    downloadFinished = true
                    assertEquals("old-target", target.readText())
                } finally {
                    releaseResponse.countDown()
                    if (!downloadFinished) {
                        ResourceFileDownloadCancellation.cancel()
                        downloadExecutor.shutdownNow()
                    }
                    assertTrue(handlerFinished.await(3, SECONDS), "The local fixture should be released")
                    downloadExecutor.shutdownNow()
                }
            }
        }
    }

    private fun profile(id: Int) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(source = ResourceFileSourceV2FlyGithub),
    )

    private inline fun <T> withFreshCancellation(block: () -> T): T = synchronized(UserHomeLock) {
        val previousUserHome = System.getProperty("user.home")
        val temporaryUserHome = Files.createTempDirectory("skipi-resource-download-home-")
        System.setProperty("user.home", temporaryUserHome.toString())
        ResourceFileDownloadCancellation.begin()
        try {
            block()
        } finally {
            ResourceFileDownloadCancellation.cancel()
            ResourceFileDownloadCancellation.begin()
            if (previousUserHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", previousUserHome)
            }
            Files.walk(temporaryUserHome).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private inline fun <T> withHttpServer(block: (HttpServer) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool(daemonThreadFactory("resource-http"))
        server.executor = executor
        server.start()
        return try {
            block(server)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private inline fun <T> withXrayRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-desktop-resource-download-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun daemonThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun assertProgressSequence(events: List<Pair<Long, Long>>, expectedTotal: Long) {
        assertTrue(events.isNotEmpty(), "A download should report progress")
        assertEquals(0L, events.first().first)
        assertEquals(expectedTotal, events.first().second)
        assertEquals(expectedTotal to expectedTotal, events.last())
        assertTrue(events.all { (_, total) -> total == expectedTotal })
        assertTrue(events.all { (downloaded, _) -> downloaded in 0..expectedTotal })
        assertTrue(
            events.zipWithNext().all { (previous, next) -> previous.first <= next.first },
            "Downloaded byte counts should be monotonic",
        )
    }

    private inner class SocksReplyServer : AutoCloseable {
        private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        private val finished = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        val username = AtomicReference<String?>()
        val password = AtomicReference<String?>()
        val port: Int get() = socket.localPort
        private val thread = Thread(::serve, "test-socks-reply").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            try {
                socket.accept().use { client ->
                    client.soTimeout = 5_000
                    val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                    val output = client.getOutputStream()

                    check(input.readUnsignedByte() == 5) { "Expected SOCKS5 greeting" }
                    val methodCount = input.readUnsignedByte()
                    val methods = ByteArray(methodCount)
                    input.readFully(methods)
                    check(methods.any { it.toInt() == 2 }) { "Client did not offer username/password auth" }
                    output.write(byteArrayOf(5, 2))
                    output.flush()

                    check(input.readUnsignedByte() == 1) { "Expected SOCKS username/password auth" }
                    val usernameBytes = ByteArray(input.readUnsignedByte())
                    input.readFully(usernameBytes)
                    val passwordBytes = ByteArray(input.readUnsignedByte())
                    input.readFully(passwordBytes)
                    username.set(usernameBytes.decodeToString())
                    password.set(passwordBytes.decodeToString())
                    output.write(byteArrayOf(1, 0))
                    output.flush()

                    check(input.readUnsignedByte() == 5) { "Expected SOCKS5 connect request" }
                    input.readUnsignedByte() // command
                    input.readUnsignedByte() // reserved
                    when (input.readUnsignedByte()) {
                        1 -> input.skipFully(4)
                        3 -> input.skipFully(input.readUnsignedByte())
                        4 -> input.skipFully(16)
                        else -> error("Unknown SOCKS address type")
                    }
                    input.readUnsignedShort() // destination port
                    output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                    output.flush()

                    readHttpHeaders(input)
                    output.write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\nsocks-body"
                            .toByteArray(Charsets.ISO_8859_1),
                    )
                    output.flush()
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }

        fun awaitFinished() {
            assertTrue(finished.await(3, SECONDS), "The SOCKS fixture should finish")
            failure.get()?.let { throw AssertionError("SOCKS fixture failed", it) }
        }

        override fun close() {
            runCatching { socket.close() }
            thread.join(3_000)
        }
    }

    private class RejectingSocksServer : AutoCloseable {
        private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        private val stopped = java.util.concurrent.atomic.AtomicBoolean()
        private val finished = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        val connectionCount = AtomicInteger()
        val port: Int get() = socket.localPort
        private val thread = Thread({
            try {
                while (!stopped.get()) {
                    socket.accept().use { connectionCount.incrementAndGet() }
                }
            } catch (error: Throwable) {
                if (!stopped.get()) failure.set(error)
            } finally {
                finished.countDown()
            }
        }, "test-socks-rejecting").apply {
            isDaemon = true
            start()
        }

        fun stopAndAwait() {
            stopped.set(true)
            runCatching { socket.close() }
            assertTrue(finished.await(3, SECONDS), "The SOCKS fixture should stop after fallback completes")
            failure.get()?.let { throw AssertionError("SOCKS fixture failed", it) }
        }

        override fun close() {
            stopped.set(true)
            runCatching { socket.close() }
            thread.join(3_000)
        }
    }

    private companion object {
        val UserHomeLock = Any()
    }

    private fun DataInputStream.skipFully(count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skipBytes(remaining)
            check(skipped > 0) { "Unexpected end of SOCKS request" }
            remaining -= skipped
        }
    }

    private fun readHttpHeaders(input: DataInputStream) {
        var matched = 0
        val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (matched < terminator.size) {
            val next = input.readUnsignedByte().toByte()
            matched = if (next == terminator[matched]) matched + 1 else if (next == terminator[0]) 1 else 0
        }
    }
}
