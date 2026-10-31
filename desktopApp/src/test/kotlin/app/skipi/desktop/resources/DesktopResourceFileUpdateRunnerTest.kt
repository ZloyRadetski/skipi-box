// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileDownloadCancelledException
import features.resources.ResourceFileKind
import features.resources.ResourceFileScope
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.ResourceFileStatus
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUpdateSource
import features.resources.runtime.ResourceFileDownloadCancellation
import features.resources.runtime.ResourceFileDownloadFailedException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopResourceFileUpdateRunnerTest {

    @Test
    fun emptyCustomPlanReturnsStatusWithoutPreparingRuntimeOrClearingCancellation() =
        withFreshCancellation {
            withXrayRoot { root ->
                val scope = ResourceFileScope(trafficConfigId = 44, resourceFileSource = 999)
                val emptyCustomFile = TrafficConfigCustomResourceFile(
                    id = 7,
                    name = "empty-rules.dat",
                    url = " \t ",
                )
                val options = ResourceFileUpdateOptions(useRunningProxy = true)
                val runner = DesktopResourceFileUpdateRunner(
                    resourceFiles = DesktopProfileResourceFiles(root),
                    resolveProxy = { error("Empty plans must not resolve a proxy") },
                    prepareFeedback = { error("Empty plans must not prepare feedback") },
                )
                ResourceFileDownloadCancellation.cancel()

                val result = runBlocking {
                    runner.execute(
                        ResourceFileUpdateRequest.Custom(
                            scope = scope,
                            file = emptyCustomFile,
                            options = options,
                            customResourceFiles = listOf(emptyCustomFile),
                        ),
                    )
                }

                assertEquals(emptyCustomFile, result.customResourceFiles.single().file)
                assertEquals(ResourceFileStatus(), result.customResourceFiles.single().status)
                assertFalse(File(root, "profiles/44").exists())
                assertTrue(ResourceFileDownloadCancellation.isCancelled())
            }
        }

    @Test
    fun allPlanDownloadsBuiltInsThenCustomInCapturedScopeAndMarksRawSourceAfterSuccess() =
        withFreshCancellation {
            withXrayRoot { root ->
                val scope = ResourceFileScope(trafficConfigId = 42, resourceFileSource = 999)
                val custom = TrafficConfigCustomResourceFile(
                    id = 18,
                    name = "custom-rules.dat",
                    url = " http://127.0.0.1:1/custom ",
                )
                val options = ResourceFileUpdateOptions(
                    useRunningProxy = true,
                    userAgent = "Profile Resource Agent/42",
                    showNotifications = false,
                )
                val otherProfile = profile(scope.copy(trafficConfigId = 43))
                val otherProfileTarget = DesktopProfileResourceFiles(root)
                    .snapshot(otherProfile)
                    .file(ResourceFileKind.GeoIp)
                    .apply {
                        parentFile.mkdirs()
                        writeText("other-profile-bytes")
                    }
                val expectedPaths = listOf(
                    "/geoip",
                    "/geosite",
                    "/geoip-only-cn-private",
                    "/direct-cidr-ipv4",
                    "/direct-cidr-ipv6",
                    "/custom",
                )
                val forcedNames = java.util.concurrent.CopyOnWriteArrayList<String>()
                val setupOrder = java.util.concurrent.CopyOnWriteArrayList<String>()
                val resolvedOptions = java.util.concurrent.CopyOnWriteArrayList<ResourceFileUpdateOptions>()
                val feedbackOptions = java.util.concurrent.CopyOnWriteArrayList<ResourceFileUpdateOptions>()
                val completed = AtomicInteger()
                val reportedFailure = AtomicReference<Throwable?>()

                SocksHttpReplyServer(expectedRequests = expectedPaths.size).use { proxyServer ->
                    val runner = DesktopResourceFileUpdateRunner(
                        resourceFiles = DesktopProfileResourceFiles(root),
                        resolveProxy = { actualOptions ->
                            setupOrder += "proxy"
                            resolvedOptions += actualOptions
                            DesktopSubscriptionSocksProxy(host = "127.0.0.1", port = proxyServer.port)
                        },
                        prepareFeedback = { actualOptions ->
                            setupOrder += "feedback"
                            feedbackOptions += actualOptions
                            DesktopResourceFileUpdateFeedback(
                                onProgress = { name, _, force -> if (force) forcedNames += name },
                                onComplete = { completed.incrementAndGet() },
                                onFailure = { reportedFailure.set(it) },
                                mapCancellationError = { it },
                            )
                        },
                    )
                    val source = updateSource(baseUrl = "http://127.0.0.1:1")

                    val result = runBlocking {
                        runner.execute(
                            ResourceFileUpdateRequest.All(
                                scope = scope,
                                source = source,
                                options = options,
                                customResourceFiles = listOf(custom),
                            ),
                        )
                    }
                    proxyServer.awaitFinished()

                    assertEquals(expectedPaths, proxyServer.requestPaths.toList())
                    assertEquals(
                        List(expectedPaths.size) { options.userAgent },
                        proxyServer.userAgents.toList(),
                    )
                    assertEquals(listOf("feedback", "proxy"), setupOrder.toList())
                    assertEquals(listOf(options), feedbackOptions.toList())
                    assertEquals(listOf(options), resolvedOptions.toList())
                    assertEquals(
                        listOf(
                            ResourceFileKind.GeoIp.displayName,
                            ResourceFileKind.GeoSite.displayName,
                            ResourceFileKind.GeoIpOnlyCnPrivate.displayName,
                            ResourceFileKind.DirectCidrIpv4.displayName,
                            ResourceFileKind.DirectCidrIpv6.displayName,
                            custom.name,
                        ),
                        forcedNames.toList(),
                    )
                    assertEquals(1, completed.get())
                    assertEquals(null, reportedFailure.get())
                    assertEquals("999", File(root, "profiles/42/.resource-source").readText())
                    assertEquals("other-profile-bytes", otherProfileTarget.readText())
                    assertTrue(result.geoIp.exists)
                    assertTrue(result.geoSite.exists)
                    assertTrue(result.geoIpOnlyCnPrivate.exists)
                    assertTrue(result.directCidrIpv4.exists)
                    assertTrue(result.directCidrIpv6.exists)
                    assertTrue(result.customResourceFiles.single().status.exists)
                    expectedPaths.forEach { path ->
                        val target = when (path) {
                            "/geoip" -> ResourceFileKind.GeoIp
                            "/geosite" -> ResourceFileKind.GeoSite
                            "/geoip-only-cn-private" -> ResourceFileKind.GeoIpOnlyCnPrivate
                            "/direct-cidr-ipv4" -> ResourceFileKind.DirectCidrIpv4
                            "/direct-cidr-ipv6" -> ResourceFileKind.DirectCidrIpv6
                            else -> null
                        }
                        val file = if (target != null) {
                            DesktopProfileResourceFiles(root).snapshot(profile(scope)).file(target)
                        } else {
                            DesktopProfileResourceFiles(root).snapshot(profile(scope)).file(custom)
                        }
                        assertContentEquals("$path-body".encodeToByteArray(), file.readBytes())
                    }
                }
            }
        }

    @Test
    fun singleBuiltInUsesDefaultUserAgentSkipsProxyResolutionAndDoesNotMarkSource() =
        withFreshCancellation {
            withXrayRoot { root ->
                withHttpServer { server ->
                    val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
                    val userAgents = java.util.concurrent.CopyOnWriteArrayList<String?>()
                    val body = "single-geosite".encodeToByteArray()
                    server.createContext("/single") { exchange ->
                        requests += exchange.requestURI.path
                        userAgents += exchange.requestHeaders.getFirst("User-Agent")
                        respond(exchange, 200, body)
                    }
                    val scope = ResourceFileScope(trafficConfigId = 45, resourceFileSource = 3)
                    val options = ResourceFileUpdateOptions(
                        useRunningProxy = false,
                        userAgent = " \t ",
                    )
                    val completed = AtomicInteger()
                    val runner = DesktopResourceFileUpdateRunner(
                        resourceFiles = DesktopProfileResourceFiles(root),
                        resolveProxy = { error("useRunningProxy=false must skip proxy resolution") },
                        prepareFeedback = { actualOptions ->
                            assertEquals(options, actualOptions)
                            DesktopResourceFileUpdateFeedback(
                                onProgress = { _, _, _ -> },
                                onComplete = { completed.incrementAndGet() },
                                onFailure = { throw AssertionError("Unexpected update failure", it) },
                                mapCancellationError = { it },
                            )
                        },
                    )
                    val targetProfile = profile(scope)
                    val expectedTarget = DesktopProfileResourceFiles(root)
                        .snapshot(targetProfile)
                        .file(ResourceFileKind.GeoSite)
                    val result = runBlocking {
                        runner.execute(
                            ResourceFileUpdateRequest.BuiltIn(
                                scope = scope,
                                kind = ResourceFileKind.GeoSite,
                                source = updateSource(baseUrl = "http://127.0.0.1:${server.address.port}").copy(
                                    geoSiteUrl = "http://127.0.0.1:${server.address.port}/single",
                                ),
                                options = options,
                                customResourceFiles = emptyList(),
                            ),
                        )
                    }

                    assertEquals(listOf("/single"), requests.toList())
                    assertEquals(listOf("SKIPI/1.0"), userAgents.toList())
                    assertContentEquals(body, expectedTarget.readBytes())
                    assertTrue(result.geoSite.exists)
                    assertFalse(File(root, "profiles/45/.resource-source").exists())
                    assertEquals(1, completed.get())
                }
            }
        }

    @Test
    fun allPlanFailureOnSecondFileKeepsPublishedPrefixStopsLaterTargetsAndDoesNotMarkSource() =
        withFreshCancellation {
            withXrayRoot { root ->
                withHttpServer { server ->
                    val requestedPaths = java.util.concurrent.CopyOnWriteArrayList<String>()
                    val successfulBody = "new-geoip".encodeToByteArray()
                    server.createContext("/") { exchange ->
                        val path = exchange.requestURI.path
                        requestedPaths += path
                        when (path) {
                            "/geoip" -> respond(exchange, 200, successfulBody)
                            "/geosite" -> respond(exchange, 500, "server-error".encodeToByteArray())
                            else -> respond(exchange, 200, "should-not-run".encodeToByteArray())
                        }
                    }
                    val scope = ResourceFileScope(trafficConfigId = 46, resourceFileSource = 4)
                    val files = DesktopProfileResourceFiles(root)
                    val snapshot = files.snapshot(profile(scope))
                    val geoIp = snapshot.file(ResourceFileKind.GeoIp).apply {
                        parentFile.mkdirs()
                        writeText("old-geoip")
                    }
                    val geoSite = snapshot.file(ResourceFileKind.GeoSite).apply { writeText("old-geosite") }
                    val third = snapshot.file(ResourceFileKind.GeoIpOnlyCnPrivate).apply { writeText("old-third") }
                    val options = ResourceFileUpdateOptions(userAgent = "Failure test agent")
                    val reportedFailure = AtomicReference<Throwable?>()
                    val completed = AtomicInteger()
                    val runner = DesktopResourceFileUpdateRunner(
                        resourceFiles = files,
                        resolveProxy = { error("useRunningProxy=false must skip proxy resolution") },
                        prepareFeedback = {
                            DesktopResourceFileUpdateFeedback(
                                onProgress = { _, _, _ -> },
                                onComplete = { completed.incrementAndGet() },
                                onFailure = { reportedFailure.set(it) },
                                mapCancellationError = { it },
                            )
                        },
                    )

                    val failure = assertFailsWith<ResourceFileDownloadFailedException> {
                        runBlocking {
                            runner.execute(
                                ResourceFileUpdateRequest.All(
                                    scope = scope,
                                    source = updateSource(baseUrl = "http://127.0.0.1:${server.address.port}"),
                                    options = options,
                                    customResourceFiles = emptyList(),
                                ),
                            )
                        }
                    }

                    assertEquals("${ResourceFileKind.GeoSite.displayName}: HTTP 500", failure.message)
                    assertEquals("HTTP 500", failure.cause?.message)
                    assertSame(failure, reportedFailure.get())
                    assertEquals(listOf("/geoip", "/geosite"), requestedPaths.toList())
                    assertContentEquals(successfulBody, geoIp.readBytes())
                    assertEquals("old-geosite", geoSite.readText())
                    assertEquals("old-third", third.readText())
                    assertFalse(File(root, "profiles/46/.resource-source").exists())
                    assertEquals(0, completed.get())
                }
            }
        }

    @Test
    fun cancellationReportsOriginalThenThrowsMappedErrorAndPreservesCurrentTarget() =
        withFreshCancellation {
            withXrayRoot { root ->
                withHttpServer { server ->
                    val requestedPaths = java.util.concurrent.CopyOnWriteArrayList<String>()
                    val largeBody = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
                    server.createContext("/") { exchange ->
                        val path = exchange.requestURI.path
                        requestedPaths += path
                        when (path) {
                            "/geoip" -> respond(exchange, 200, "first-complete".encodeToByteArray())
                            "/geosite" -> {
                                exchange.sendResponseHeaders(200, largeBody.size.toLong())
                                try {
                                    exchange.responseBody.use { it.write(largeBody) }
                                } catch (_: IOException) {
                                    // The client disconnects once cancellation is observed.
                                } finally {
                                    exchange.close()
                                }
                            }
                            else -> respond(exchange, 200, "should-not-run".encodeToByteArray())
                        }
                    }
                    val scope = ResourceFileScope(trafficConfigId = 47, resourceFileSource = 5)
                    val files = DesktopProfileResourceFiles(root)
                    val snapshot = files.snapshot(profile(scope))
                    val geoIp = snapshot.file(ResourceFileKind.GeoIp)
                    val geoSite = snapshot.file(ResourceFileKind.GeoSite).apply {
                        parentFile.mkdirs()
                        writeText("old-geosite")
                    }
                    val mappedCancellation = ResourceFileDownloadCancelledException("mapped cancellation")
                    val originalFromMapper = AtomicReference<ResourceFileDownloadCancelledException?>()
                    val reportedFailure = AtomicReference<Throwable?>()
                    val cancellationTriggered = AtomicBoolean()
                    val completed = AtomicInteger()
                    val options = ResourceFileUpdateOptions(userAgent = "Cancellation test agent")
                    val runner = DesktopResourceFileUpdateRunner(
                        resourceFiles = files,
                        resolveProxy = { error("useRunningProxy=false must skip proxy resolution") },
                        prepareFeedback = {
                            DesktopResourceFileUpdateFeedback(
                                onProgress = { name, progress, _ ->
                                    if (
                                        name == ResourceFileKind.GeoSite.displayName &&
                                        progress != null &&
                                        progress > 20 &&
                                        cancellationTriggered.compareAndSet(false, true)
                                    ) {
                                        ResourceFileDownloadCancellation.cancel()
                                    }
                                },
                                onComplete = { completed.incrementAndGet() },
                                onFailure = { reportedFailure.set(it) },
                                mapCancellationError = { original ->
                                    originalFromMapper.set(original)
                                    mappedCancellation
                                },
                            )
                        },
                    )

                    val thrown = assertFailsWith<ResourceFileDownloadCancelledException> {
                        runBlocking {
                            runner.execute(
                                ResourceFileUpdateRequest.All(
                                    scope = scope,
                                    source = updateSource(baseUrl = "http://127.0.0.1:${server.address.port}"),
                                    options = options,
                                    customResourceFiles = emptyList(),
                                ),
                            )
                        }
                    }

                    assertTrue(cancellationTriggered.get())
                    assertSame(originalFromMapper.get(), reportedFailure.get())
                    assertIs<ResourceFileDownloadCancelledException>(reportedFailure.get())
                    assertSame(mappedCancellation, thrown)
                    assertEquals("mapped cancellation", thrown.message)
                    assertEquals(0, completed.get())
                    assertEquals(listOf("/geoip", "/geosite"), requestedPaths.toList())
                    assertEquals("first-complete", geoIp.readText())
                    assertEquals("old-geosite", geoSite.readText())
                    assertFalse(File(root, "profiles/47/.resource-source").exists())
                }
            }
        }

    @Test
    fun selectedProxyFromResolverIsUsedAndIoFailureFallsBackToOneDirectRequest() =
        withFreshCancellation {
            withXrayRoot { root ->
                withHttpServer { server ->
                    val directRequests = AtomicInteger()
                    val body = "direct-after-proxy-failure".encodeToByteArray()
                    server.createContext("/resource") { exchange ->
                        directRequests.incrementAndGet()
                        respond(exchange, 200, body)
                    }
                    val scope = ResourceFileScope(trafficConfigId = 48, resourceFileSource = 2)
                    val options = ResourceFileUpdateOptions(
                        useRunningProxy = true,
                        userAgent = "Proxy route test agent",
                    )
                    val resolverOptions = java.util.concurrent.CopyOnWriteArrayList<ResourceFileUpdateOptions>()
                    val preparedOptions = java.util.concurrent.CopyOnWriteArrayList<ResourceFileUpdateOptions>()
                    val completed = AtomicInteger()
                    val reportedFailure = AtomicReference<Throwable?>()

                    RejectingSocksServer().use { proxyServer ->
                        val selectedProxy = DesktopSubscriptionSocksProxy(
                            host = "127.0.0.1",
                            port = proxyServer.port,
                        )
                        val runner = DesktopResourceFileUpdateRunner(
                            resourceFiles = DesktopProfileResourceFiles(root),
                            resolveProxy = { actualOptions ->
                                resolverOptions += actualOptions
                                selectedProxy
                            },
                            prepareFeedback = { actualOptions ->
                                preparedOptions += actualOptions
                                DesktopResourceFileUpdateFeedback(
                                    onProgress = { _, _, _ -> },
                                    onComplete = { completed.incrementAndGet() },
                                    onFailure = { reportedFailure.set(it) },
                                    mapCancellationError = { it },
                                )
                            },
                        )
                        val target = DesktopProfileResourceFiles(root)
                            .snapshot(profile(scope))
                            .file(ResourceFileKind.GeoIp)
                        val result = runBlocking {
                            runner.execute(
                                ResourceFileUpdateRequest.BuiltIn(
                                    scope = scope,
                                    kind = ResourceFileKind.GeoIp,
                                    source = updateSource(baseUrl = "http://127.0.0.1:${server.address.port}").copy(
                                        geoIpUrl = "http://127.0.0.1:${server.address.port}/resource",
                                    ),
                                    options = options,
                                    customResourceFiles = emptyList(),
                                ),
                            )
                        }
                        proxyServer.stopAndAwait()

                        assertEquals(1, resolverOptions.size)
                        assertSame(options, resolverOptions.single())
                        assertEquals(listOf(options), preparedOptions.toList())
                        assertTrue(proxyServer.connectionCount.get() > 0, "Resolver proxy should be attempted")
                        assertEquals(1, directRequests.get())
                        assertContentEquals(body, target.readBytes())
                        assertTrue(result.geoIp.exists)
                        assertEquals(1, completed.get())
                        assertEquals(null, reportedFailure.get())
                        assertFalse(File(root, "profiles/48/.resource-source").exists())
                    }
                }
            }
        }

    private fun updateSource(baseUrl: String): ResourceFileUpdateSource = ResourceFileUpdateSource(
        id = ResourceFileSourceV2FlyGithub,
        geoIpUrl = "$baseUrl/geoip",
        geoSiteUrl = "$baseUrl/geosite",
        geoIpOnlyCnPrivateUrl = "$baseUrl/geoip-only-cn-private",
        directCidrIpv4Url = "$baseUrl/direct-cidr-ipv4",
        directCidrIpv6Url = "$baseUrl/direct-cidr-ipv6",
    )

    private fun profile(scope: ResourceFileScope) = TrafficConfigState(
        id = scope.trafficConfigId,
        name = "Profile ${scope.trafficConfigId}",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(source = scope.resourceFileSource),
    )

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray) {
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private inline fun <T> withHttpServer(block: (HttpServer) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool(daemonThreadFactory("resource-runner-http"))
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
        val root = Files.createTempDirectory("skipi-desktop-resource-runner-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private inline fun <T> withFreshCancellation(block: () -> T): T = synchronized(UserHomeLock) {
        val previousUserHome = System.getProperty("user.home")
        val temporaryUserHome = Files.createTempDirectory("skipi-resource-runner-home-")
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

    private fun daemonThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private class SocksHttpReplyServer(private val expectedRequests: Int) : AutoCloseable {
        private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        private val finished = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        val requestPaths = java.util.concurrent.CopyOnWriteArrayList<String>()
        val userAgents = java.util.concurrent.CopyOnWriteArrayList<String?>()
        val port: Int get() = socket.localPort
        private val thread = Thread(::serve, "resource-runner-socks-http").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            try {
                repeat(expectedRequests) {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                        val output = client.getOutputStream()
                        check(input.readUnsignedByte() == 5) { "Expected SOCKS5 greeting" }
                        val methodCount = input.readUnsignedByte()
                        val methods = ByteArray(methodCount)
                        input.readFully(methods)
                        check(methods.any { it.toInt() == 0 }) { "Client did not offer no-auth SOCKS" }
                        output.write(byteArrayOf(5, 0))
                        output.flush()

                        check(input.readUnsignedByte() == 5) { "Expected SOCKS5 connect request" }
                        check(input.readUnsignedByte() == 1) { "Expected SOCKS5 CONNECT" }
                        input.readUnsignedByte() // reserved
                        when (input.readUnsignedByte()) {
                            1 -> input.skipFully(4)
                            3 -> input.skipFully(input.readUnsignedByte())
                            4 -> input.skipFully(16)
                            else -> error("Unknown SOCKS address type")
                        }
                        input.readUnsignedShort() // destination port
                        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
                        output.flush()

                        val headers = readHttpHeaders(input)
                        val requestLine = headers.lineSequence().firstOrNull().orEmpty()
                        val path = requestLine.split(' ').getOrNull(1)
                            ?: error("Invalid HTTP request line: $requestLine")
                        requestPaths += path
                        userAgents += headers.lineSequence()
                            .firstOrNull { line -> line.startsWith("User-Agent:", ignoreCase = true) }
                            ?.substringAfter(':')
                            ?.trim()
                        val body = "$path-body".encodeToByteArray()
                        output.write(
                            "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray(Charsets.ISO_8859_1),
                        )
                        output.write(body)
                        output.flush()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }

        private fun DataInputStream.skipFully(count: Int) {
            var remaining = count
            while (remaining > 0) {
                val skipped = skipBytes(remaining)
                check(skipped > 0) { "Unexpected end of SOCKS request" }
                remaining -= skipped
            }
        }

        private fun readHttpHeaders(input: DataInputStream): String {
            val bytes = ByteArrayOutputStream()
            var matched = 0
            val terminator = byteArrayOf(
                '\r'.code.toByte(),
                '\n'.code.toByte(),
                '\r'.code.toByte(),
                '\n'.code.toByte(),
            )
            while (matched < terminator.size) {
                val next = input.readUnsignedByte().toByte()
                bytes.write(next.toInt())
                matched = if (next == terminator[matched]) {
                    matched + 1
                } else if (next == terminator[0]) {
                    1
                } else {
                    0
                }
            }
            return bytes.toString(Charsets.ISO_8859_1)
        }

        fun awaitFinished() {
            assertTrue(finished.await(5, SECONDS), "The SOCKS fixture should finish")
            failure.get()?.let { throw AssertionError("SOCKS HTTP fixture failed", it) }
        }

        override fun close() {
            runCatching { socket.close() }
            thread.join(3_000)
        }
    }

    private class RejectingSocksServer : AutoCloseable {
        private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        private val stopped = AtomicBoolean()
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
        }, "resource-runner-socks-rejecting").apply {
            isDaemon = true
            start()
        }

        fun stopAndAwait() {
            stopped.set(true)
            runCatching { socket.close() }
            assertTrue(finished.await(3, SECONDS), "The SOCKS fixture should stop after fallback")
            failure.get()?.let { throw AssertionError("SOCKS rejecting fixture failed", it) }
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
}
