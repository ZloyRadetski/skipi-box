// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/** Opt-in: the Home connection log through the real Core library over FFM. */
class DesktopConnectionLogRealCoreTest {
    @Test
    fun aConnectionMadeByIpIsListedUnderItsSniffedName() {
        val runtimeDirectory = System.getenv("SKIPI_CORE_INTEGRATION_DIR")?.takeIf(String::isNotBlank) ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        val log = DesktopConnectionLog(controller::readConnectionLog)
        val socksPort = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use(ServerSocket::getLocalPort)
        val target = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching { target.accept().use { socket -> socket.getInputStream().read(ByteArray(256)) } }
        }
        val config = """
            {
              "log": { "loglevel": "none" },
              "inbounds": [{
                "tag": "in", "listen": "127.0.0.1", "port": $socksPort, "protocol": "socks",
                "settings": { "auth": "noauth" },
                "sniffing": { "enabled": true, "destOverride": ["http", "tls"], "routeOnly": true }
              }],
              "outbounds": [{ "protocol": "freedom", "tag": "direct" }]
            }
        """.trimIndent()
        try {
            controller.setConnectionLogEnabled(true)
            controller.start(config).getOrThrow()

            connectWhenReady(socksPort).use { socket ->
                socket.soTimeout = 3_000
                val input = DataInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                output.write(byteArrayOf(5, 1, 0))
                input.readFully(ByteArray(2))
                val port = target.localPort
                output.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, (port shr 8).toByte(), port.toByte()))
                input.readFully(ByteArray(10))
                output.write("GET / HTTP/1.1\r\nHost: real-core.test\r\n\r\n".toByteArray())

                repeat(150) {
                    log.poll()
                    if (log.state.value.rows.isNotEmpty()) return@repeat
                    Thread.sleep(20)
                }
            }

            val row = log.state.value.rows.single()
            assertEquals("real-core.test", row.host)
            assertEquals("direct", row.outbound)
        } finally {
            target.close()
            controller.close()
        }
    }

    private fun connectWhenReady(port: Int): Socket {
        repeat(100) {
            runCatching { return Socket("127.0.0.1", port) }
            Thread.sleep(10)
        }
        error("Core did not open its SOCKS listener on $port")
    }
}
