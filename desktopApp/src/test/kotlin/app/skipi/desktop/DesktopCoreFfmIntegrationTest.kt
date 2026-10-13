// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Optional end-to-end test for a real shared Core library. It is intentionally
 * opt-in because normal source tests do not carry platform-native binaries.
 */
class DesktopCoreFfmIntegrationTest {
    @Test
    fun startsAndStopsTheBundledCoreInsideTheDesktopJvm() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val controller = DesktopCoreController(
            runtimeProvider = { Result.success(runtime) },
        )
        try {
            val started = controller.start(testXrayConfig()).getOrThrow()
            assertTrue(started.isRunning)
            assertTrue(started.coreVersion.orEmpty().isNotBlank())

            val stats = controller.queryTrafficStats().getOrThrow()
            assertTrue(stats.contains("\"inbound\""))
            assertTrue(stats.contains("\"outbound\""))

            assertFalse(controller.stop().getOrThrow().isRunning)
            assertTrue(controller.start(testXrayConfig()).getOrThrow().isRunning)
            assertFalse(controller.stop().getOrThrow().isRunning)
        } finally {
            controller.close()
        }
    }

    @Test
    fun sharedAuthenticatedSocksInboundAcceptsTheConfiguredCredentialsInNativeCore() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use(ServerSocket::getLocalPort)
        val inbound = LocalProxyXrayConfigFactory.buildSocksInbound(
            LocalProxyXrayConfigOptions(
                socksPort = port,
                listenAddress = "127.0.0.1",
                username = "integration-user",
                password = "integration-secret",
            ),
        )
        val config = """
            {
              "log": { "loglevel": "none" },
              "inbounds": [$inbound],
              "outbounds": [{ "protocol": "freedom", "tag": "direct" }]
            }
        """.trimIndent()
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        try {
            assertTrue(controller.start(config).getOrThrow().isRunning)
            connectWhenReady(port).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                socket.soTimeout = 2_000

                output.write(byteArrayOf(5, 1, 2))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(2, input.readUnsignedByte())

                val username = "integration-user".encodeToByteArray()
                val password = "integration-secret".encodeToByteArray()
                output.writeByte(1)
                output.writeByte(username.size)
                output.write(username)
                output.writeByte(password.size)
                output.write(password)
                output.flush()

                assertEquals(1, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
            }
            assertFalse(controller.stop().getOrThrow().isRunning)
        } finally {
            controller.close()
        }
    }

    private fun connectWhenReady(port: Int): Socket {
        repeat(100) {
            runCatching {
                return Socket("127.0.0.1", port)
            }
            Thread.sleep(10)
        }
        error("Native Core did not open its authenticated SOCKS listener on $port")
    }

    private fun testXrayConfig(): String {
        val port = ServerSocket(0).use(ServerSocket::getLocalPort)
        return """
            {
              "log": { "loglevel": "none" },
              "inbounds": [
                {
                  "listen": "127.0.0.1",
                  "port": $port,
                  "protocol": "http",
                  "settings": {}
                }
              ],
              "routing": {
                "rules": [
                  {
                    "type": "field",
                    "domain": ["geosite:geolocation-!cn"],
                    "outboundTag": "direct"
                  }
                ]
              },
              "outbounds": [
                { "protocol": "freedom", "tag": "direct" }
              ]
            }
        """.trimIndent()
    }

    private companion object {
        const val IntegrationRuntimeDirectoryEnv = "SKIPI_CORE_INTEGRATION_DIR"
    }
}
