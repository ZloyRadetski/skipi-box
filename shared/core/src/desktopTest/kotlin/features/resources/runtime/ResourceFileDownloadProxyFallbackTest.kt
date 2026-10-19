// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceFileDownloadProxyFallbackTest {
    @Test
    fun authenticatesWholeProxiedTransferRetriesLogsFallbackThenRunsDirectTransfer() {
        val events = mutableListOf<String>()
        val retryDelays = mutableListOf<Long>()
        val target = MemoryTarget()
        val proxy = TestProxy(username = "proxy-user")
        val proxyConnections = mutableListOf<FakeConnection>()
        val proxyAttempts = RecordingAttemptFactory { attempt ->
            events += "proxy.begin." + attempt
            FakeOpener { uri ->
                assertEquals(URI("https://resources.example.test/file"), uri)
                events += "proxy.open." + attempt
                FakeConnection(
                    responseCodeFailure = IOException("proxy connection failed"),
                    onDisconnect = { events += "proxy.disconnect." + attempt },
                ).also(proxyConnections::add)
            }
        }
        val proxyTransfer = transfer(
            attempts = proxyAttempts,
            target = target,
            onRetry = { delay ->
                retryDelays += delay
                events += "retry." + delay
            },
        )
        val directBody = "direct response".encodeToByteArray()
        val directAttempts = RecordingAttemptFactory { attempt ->
            events += "direct.begin." + attempt
            FakeOpener {
                events += "direct.open." + attempt
                FakeConnection(
                    responseCodeValue = 200,
                    contentLength = directBody.size.toLong(),
                    body = ByteArrayInputStream(directBody),
                    onDisconnect = { events += "direct.disconnect." + attempt },
                )
            }
        }
        val directTransfer = transfer(directAttempts, target)

        runResourceFileDownloadWithProxyFallback(
            proxy = proxy,
            usernameOf = TestProxy::username,
            withAuthenticator = { _, block ->
                events += "auth.begin"
                try {
                    block()
                } finally {
                    events += "auth.end"
                }
            },
            onProxyFallback = { events += "fallback.log" },
            downloadViaProxy = { usedProxy ->
                assertEquals(proxy, usedProxy)
                events += "proxy.download"
                proxyTransfer.download("https://resources.example.test/file") { _, _ -> }
            },
            downloadDirect = {
                events += "direct.download"
                directTransfer.download("https://resources.example.test/file") { _, _ -> }
            },
        )

        assertEquals(3, proxyAttempts.beginCount)
        assertEquals(listOf(1_000L, 2_000L), retryDelays)
        assertEquals(1, directAttempts.beginCount)
        assertTrue(proxyConnections.all { it.disconnectCount == 1 })
        assertEquals(1, target.publishCount)
        assertContentEquals(directBody, target.publishedBytes)
        assertEquals(
            listOf(
                "auth.begin",
                "proxy.download",
                "proxy.begin.1",
                "proxy.open.1",
                "proxy.disconnect.1",
                "retry.1000",
                "proxy.begin.2",
                "proxy.open.2",
                "proxy.disconnect.2",
                "retry.2000",
                "proxy.begin.3",
                "proxy.open.3",
                "proxy.disconnect.3",
                "auth.end",
                "fallback.log",
                "direct.download",
                "direct.begin.1",
                "direct.open.1",
                "direct.disconnect.1",
            ),
            events,
        )
    }

    @Test
    fun nonIoProxyFailurePropagatesWithoutFallbackOrDirectTransfer() {
        val events = mutableListOf<String>()
        val error = assertFailsWith<IllegalStateException> {
            runResourceFileDownloadWithProxyFallback(
                proxy = TestProxy(username = "proxy-user"),
                usernameOf = TestProxy::username,
                withAuthenticator = { _, block ->
                    events += "auth.begin"
                    try {
                        block()
                    } finally {
                        events += "auth.end"
                    }
                },
                onProxyFallback = { events += "fallback.log" },
                downloadViaProxy = { usedProxy ->
                    assertEquals("proxy-user", usedProxy.username)
                    events += "proxy.download"
                    throw IllegalStateException("HTTP 500")
                },
                downloadDirect = { events += "direct.download" },
            )
        }

        assertEquals("HTTP 500", error.message)
        assertEquals(listOf("auth.begin", "proxy.download", "auth.end"), events)
        assertFalse("fallback.log" in events)
        assertFalse("direct.download" in events)
    }

    @Test
    fun blankUsernameUsesProxyAndSkipsAuthenticatorBeforeIoFallback() {
        val events = mutableListOf<String>()
        var authenticatorCalls = 0
        var proxyCalls = 0
        var directCalls = 0

        runResourceFileDownloadWithProxyFallback(
            proxy = TestProxy(username = " \t"),
            usernameOf = TestProxy::username,
            withAuthenticator = { _, block ->
                authenticatorCalls += 1
                block()
            },
            onProxyFallback = { events += "fallback.log" },
            downloadViaProxy = { usedProxy ->
                assertEquals(" \t", usedProxy.username)
                proxyCalls += 1
                events += "proxy.download"
                throw IOException("proxy failed")
            },
            downloadDirect = {
                directCalls += 1
                events += "direct.download"
            },
        )

        assertEquals(0, authenticatorCalls)
        assertEquals(1, proxyCalls)
        assertEquals(1, directCalls)
        assertEquals(listOf("proxy.download", "fallback.log", "direct.download"), events)
    }

    @Test
    fun nullProxySelectsDirectWithoutProxyAuthOrFallback() {
        var authenticatorCalls = 0
        var proxyCalls = 0
        var fallbackCalls = 0
        var directCalls = 0

        runResourceFileDownloadWithProxyFallback<TestProxy>(
            proxy = null,
            usernameOf = { error("no proxy username should be requested") },
            withAuthenticator = { _, block ->
                authenticatorCalls += 1
                block()
            },
            onProxyFallback = { fallbackCalls += 1 },
            downloadViaProxy = { proxyCalls += 1 },
            downloadDirect = { directCalls += 1 },
        )

        assertEquals(0, authenticatorCalls)
        assertEquals(0, proxyCalls)
        assertEquals(0, fallbackCalls)
        assertEquals(1, directCalls)
    }

    private fun transfer(
        attempts: ResourceFileDownloadAttemptFactory,
        target: MemoryTarget,
        onRetry: (Long) -> Unit = {},
    ) = ResourceFileDownloadTransfer(
        attempts = attempts,
        publishAtomically = target::publishAtomically,
        isCancelled = { false },
        throwIfCancelled = {},
        waitBeforeRetry = onRetry,
    )

    private data class TestProxy(val username: String)

    private class RecordingAttemptFactory(
        private val openerForAttempt: (Int) -> ResourceFileDownloadConnectionOpener,
    ) : ResourceFileDownloadAttemptFactory {
        var beginCount = 0
            private set

        override fun beginAttempt(): ResourceFileDownloadConnectionOpener {
            beginCount += 1
            return openerForAttempt(beginCount)
        }
    }

    private class FakeOpener(
        private val openConnection: (URI) -> ResourceFileDownloadConnection,
    ) : ResourceFileDownloadConnectionOpener {
        override fun open(uri: URI): ResourceFileDownloadConnection = openConnection(uri)
    }

    private class FakeConnection(
        private val responseCodeValue: Int = 200,
        private val responseCodeFailure: IOException? = null,
        override val location: String? = null,
        override val contentLength: Long = -1L,
        private val body: InputStream = ByteArrayInputStream(byteArrayOf()),
        private val onDisconnect: () -> Unit = {},
    ) : ResourceFileDownloadConnection {
        var disconnectCount = 0
            private set

        override val responseCode: Int
            get() {
                responseCodeFailure?.let { throw it }
                return responseCodeValue
            }

        override val inputStream: InputStream
            get() = body

        override fun disconnect() {
            disconnectCount += 1
            onDisconnect()
        }
    }

    private class MemoryTarget {
        var publishedBytes: ByteArray = byteArrayOf()
            private set
        var publishCount: Int = 0
            private set

        fun publishAtomically(write: (OutputStream) -> Unit) {
            publishCount += 1
            val staged = ByteArrayOutputStream()
            write(staged)
            publishedBytes = staged.toByteArray()
        }
    }
}

