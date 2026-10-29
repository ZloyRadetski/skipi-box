// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import features.resources.ResourceFileDownloadCancelledException

/** Android source oracle for the existing HTTP resource downloader behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = android.app.Application::class,
    shadows = [RealOsRenameShadow::class],
)
class AndroidResourceFileDownloaderOracleTest {
    @Before
    fun resetCancellationState() {
        ResourceFileDownloadCancellation.begin()
    }

    @Test
    fun getUsesDefaultUserAgentAndAtomicallyReplacesExistingTarget() = withTempDirectory { directory ->
        val requestMethod = Collections.synchronizedList(mutableListOf<String>())
        val userAgents = Collections.synchronizedList(mutableListOf<String?>())
        val body = "fresh geo data".encodeToByteArray()
        val target = File(directory, "geoip.dat").apply { writeText("old geo data") }

        withServer { server ->
            server.createContext("/geoip") { exchange ->
                requestMethod += exchange.requestMethod
                userAgents += exchange.requestHeaders.getFirst("User-Agent")
                exchange.respond(200, body)
            }

            AndroidResourceFileDownloader(context = null).download(
                url = "http://127.0.0.1:${server.address.port}/geoip",
                target = target,
            )
        }

        assertEquals(listOf("GET"), requestMethod)
        assertEquals(listOf("SKIPI/1.0"), userAgents)
        assertEquals(body.toList(), target.readBytes().toList())
        assertTrue(target.length() > 0L)
        assertEquals(emptyList<File>(), directory.listFiles()?.filter { it != target }.orEmpty())
    }

    @Test
    fun followsRelativeRedirectToResolvedPath() = withTempDirectory { directory ->
        val requestedPaths = Collections.synchronizedList(mutableListOf<String>())
        val target = File(directory, "geosite.dat").apply { writeText("old") }

        withServer { server ->
            server.createContext("/redirect/start") { exchange ->
                requestedPaths += exchange.requestURI.path
                exchange.responseHeaders.add("Location", "../payload")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/payload") { exchange ->
                requestedPaths += exchange.requestURI.path
                exchange.respond(200, "redirected payload".encodeToByteArray())
            }

            AndroidResourceFileDownloader(context = null).download(
                url = "http://127.0.0.1:${server.address.port}/redirect/start",
                target = target,
            )
        }

        assertEquals(listOf("/redirect/start", "/payload"), requestedPaths)
        assertEquals("redirected payload", target.readText())
    }

    @Test
    fun permitsFourRedirectsAcrossFiveRequestHopsAndRejectsAFifthRedirect() = withTempDirectory { directory ->
        val successRequests = AtomicInteger()
        val successTarget = File(directory, "success.dat")

        withServer { server ->
            server.createContext("/success/") { exchange ->
                val hop = exchange.requestURI.path.substringAfterLast('/').toInt()
                successRequests.incrementAndGet()
                if (hop < 4) {
                    exchange.responseHeaders.add("Location", (hop + 1).toString())
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                } else {
                    exchange.respond(200, "last allowed hop".encodeToByteArray())
                }
            }

            AndroidResourceFileDownloader(context = null).download(
                url = "http://127.0.0.1:${server.address.port}/success/0",
                target = successTarget,
            )
        }

        assertEquals(5, successRequests.get())
        assertEquals("last allowed hop", successTarget.readText())

        val rejectedRequests = AtomicInteger()
        val rejectedTarget = File(directory, "rejected.dat").apply { writeText("previous") }
        val error = withServer { server ->
            server.createContext("/reject/") { exchange ->
                val hop = exchange.requestURI.path.substringAfterLast('/').toInt()
                rejectedRequests.incrementAndGet()
                if (hop < 5) {
                    exchange.responseHeaders.add("Location", (hop + 1).toString())
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                } else {
                    exchange.respond(200, "must not be requested".encodeToByteArray())
                }
            }

            runCatching {
                AndroidResourceFileDownloader(context = null).download(
                    url = "http://127.0.0.1:${server.address.port}/reject/0",
                    target = rejectedTarget,
                )
            }.exceptionOrNull()
        }

        assertTrue("expected the source redirect-limit failure, got $error", error is IllegalStateException)
        assertEquals("Too many redirects", error?.message)
        assertEquals(5, rejectedRequests.get())
        assertEquals("previous", rejectedTarget.readText())
    }

    @Test
    fun httpFailureIsNotRetriedAndLeavesExistingTargetIntact() = withTempDirectory { directory ->
        val requests = AtomicInteger()
        val target = File(directory, "geoip.dat").apply { writeText("previous") }
        val error = withServer { server ->
            server.createContext("/failure") { exchange ->
                requests.incrementAndGet()
                exchange.respond(500, "server error".encodeToByteArray())
            }

            runCatching {
                AndroidResourceFileDownloader(context = null).download(
                    url = "http://127.0.0.1:${server.address.port}/failure",
                    target = target,
                )
            }.exceptionOrNull()
        }

        assertTrue("expected non-I/O HTTP failure, got $error", error is IllegalStateException)
        assertEquals("HTTP 500", error?.message)
        assertEquals(1, requests.get())
        assertEquals("previous", target.readText())
    }

    @Test
    fun emptySuccessBodyIsRejectedAndLeavesExistingTargetIntact() = withTempDirectory { directory ->
        val target = File(directory, "geosite.dat").apply { writeText("previous") }
        val error = withServer { server ->
            server.createContext("/empty") { exchange ->
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }

            runCatching {
                AndroidResourceFileDownloader(context = null).download(
                    url = "http://127.0.0.1:${server.address.port}/empty",
                    target = target,
                )
            }.exceptionOrNull()
        }

        assertTrue("expected empty-file failure, got $error", error is IllegalStateException)
        assertEquals("${target.name} is empty", error?.message)
        assertEquals("previous", target.readText())
        assertEquals(listOf(target), directory.listFiles()?.toList())
    }

    @Test
    fun progressReportsKnownAndUnknownContentLength() = withTempDirectory { directory ->
        val knownBody = "known-size-payload".encodeToByteArray()
        val knownProgress = mutableListOf<Pair<Long, Long>>()
        val unknownProgress = mutableListOf<Pair<Long, Long>>()

        withServer { server ->
            server.createContext("/known") { exchange -> exchange.respond(200, knownBody) }
            server.createContext("/unknown") { exchange -> exchange.respondChunked(200, "chunked-payload".encodeToByteArray()) }

            val downloader = AndroidResourceFileDownloader(context = null)
            downloader.download(
                url = "http://127.0.0.1:${server.address.port}/known",
                target = File(directory, "known.dat"),
                onProgress = { downloaded, total -> knownProgress += downloaded to total },
            )
            downloader.download(
                url = "http://127.0.0.1:${server.address.port}/unknown",
                target = File(directory, "unknown.dat"),
                onProgress = { downloaded, total -> unknownProgress += downloaded to total },
            )
        }

        assertTrue(knownProgress.isNotEmpty())
        assertEquals(0L to knownBody.size.toLong(), knownProgress.first())
        assertEquals(knownBody.size.toLong() to knownBody.size.toLong(), knownProgress.last())
        assertTrue(knownProgress.all { (_, total) -> total == knownBody.size.toLong() })

        assertTrue(unknownProgress.isNotEmpty())
        assertEquals(0L to -1L, unknownProgress.first())
        assertEquals("chunked-payload".length.toLong() to -1L, unknownProgress.last())
        assertTrue(unknownProgress.all { (_, total) -> overallProgress(0, 1, 1, total) == null })
    }

    @Test
    fun overallProgressKeepsItsExistingBoundsAndUnknownLengthRules() {
        assertNull(overallProgress(fileIndex = 0, fileCount = 1, downloadedBytes = 0, totalBytes = 0))
        assertNull(overallProgress(fileIndex = 0, fileCount = 0, downloadedBytes = 1, totalBytes = 2))
        assertEquals(25, overallProgress(fileIndex = -1, fileCount = 2, downloadedBytes = 5, totalBytes = 10))
        assertEquals(75, overallProgress(fileIndex = 1, fileCount = 2, downloadedBytes = 5, totalBytes = 10))
        assertEquals(100, overallProgress(fileIndex = 2, fileCount = 2, downloadedBytes = 0, totalBytes = 10))
        assertEquals(50, overallProgress(fileIndex = 0, fileCount = 2, downloadedBytes = 20, totalBytes = 10))
    }

    @Test
    fun preCancelledDownloadDoesNotSendARequestOrReplaceTheTarget() = withTempDirectory { directory ->
        val target = File(directory, "geoip.dat").apply { writeText("previous") }
        val requests = AtomicInteger()
        val error = withServer { server ->
            server.createContext("/pre-cancelled") { exchange ->
                requests.incrementAndGet()
                exchange.respond(200, "must not be downloaded".encodeToByteArray())
            }

            ResourceFileDownloadCancellation.cancel()
            runCatching {
                AndroidResourceFileDownloader(context = null).download(
                    url = "http://127.0.0.1:${server.address.port}/pre-cancelled",
                    target = target,
                )
            }.exceptionOrNull()
        }

        assertTrue("expected cancellation, got $error", error is ResourceFileDownloadCancelledException)
        assertEquals(0, requests.get())
        assertEquals("previous", target.readText())
        assertEquals(listOf(target), directory.listFiles()?.toList())
    }

    @Test
    fun cancellationDuringProgressDoesNotReplaceTheTarget() = withTempDirectory { directory ->
        val target = File(directory, "geosite.dat").apply { writeText("previous") }
        val requests = AtomicInteger()
        val cancellationSent = AtomicBoolean(false)
        val body = ByteArray(32 * 1024) { 'x'.code.toByte() }
        val error = withServer { server ->
            server.createContext("/cancel-during-progress") { exchange ->
                requests.incrementAndGet()
                exchange.respond(200, body)
            }

            runCatching {
                AndroidResourceFileDownloader(context = null).download(
                    url = "http://127.0.0.1:${server.address.port}/cancel-during-progress",
                    target = target,
                    onProgress = { downloadedBytes, _ ->
                        if (downloadedBytes > 0L && cancellationSent.compareAndSet(false, true)) {
                            ResourceFileDownloadCancellation.cancel()
                        }
                    },
                )
            }.exceptionOrNull()
        }

        assertTrue("expected cancellation, got $error", error is ResourceFileDownloadCancelledException)
        assertTrue(cancellationSent.get())
        assertEquals(1, requests.get())
        assertEquals("previous", target.readText())
        assertEquals(listOf(target), directory.listFiles()?.toList())
    }
}

private inline fun <T> withServer(block: (HttpServer) -> T): T {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.start()
    try {
        return block(server)
    } finally {
        server.stop(0)
    }
}

private inline fun <T> withTempDirectory(block: (File) -> T): T {
    val directory = Files.createTempDirectory("android-resource-download-oracle-").toFile()
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

private fun HttpExchange.respondChunked(code: Int, body: ByteArray) {
    sendResponseHeaders(code, 0L)
    responseBody.use { it.write(body) }
}
