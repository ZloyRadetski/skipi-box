// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The live-connection delay is the time to the first HTTP answer over the host's route. */
class JvmDiagnosticsLatencyProbeTest {
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
    fun anEmpty204AnswerCountsAsAMeasuredDelay() = runBlocking {
        val server = httpServer { exchange -> exchange.sendResponseHeaders(204, -1) }

        val millis = JvmDiagnosticsHttpClient(DiagnosticsRoute.Direct).measureMillis(server.url("/generate_204"), 3_000)

        assertNotNull(millis)
        assertTrue(millis in 0..3_000, "delay was $millis")
    }

    @Test
    fun theDelayCoversTheServersThinkingTime() = runBlocking {
        val server = httpServer { exchange ->
            Thread.sleep(300)
            exchange.sendResponseHeaders(204, -1)
        }

        val millis = JvmDiagnosticsHttpClient(DiagnosticsRoute.Direct).measureMillis(server.url("/slow"), 3_000)

        assertNotNull(millis)
        assertTrue(millis >= 250, "delay was $millis")
    }

    @Test
    fun serverErrorsAndUnreachableHostsAreFailures() = runBlocking {
        val server = httpServer { exchange -> exchange.sendResponseHeaders(503, -1) }
        val client = JvmDiagnosticsHttpClient(DiagnosticsRoute.Direct)

        assertNull(client.measureMillis(server.url("/down"), 3_000))
        val dead = ServerSocket(0).use { it.localPort }
        assertNull(client.measureMillis("http://127.0.0.1:$dead/", 3_000))
    }

    @Test
    fun anAnswerThatNeverComesFailsWithinTheTimeout() = runBlocking {
        val server = httpServer { Thread.sleep(3_000) }
        val started = System.nanoTime()

        val millis = JvmDiagnosticsHttpClient(DiagnosticsRoute.Direct).measureMillis(server.url("/hang"), 400)

        assertNull(millis)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "the probe waited past its timeout")
    }
}
