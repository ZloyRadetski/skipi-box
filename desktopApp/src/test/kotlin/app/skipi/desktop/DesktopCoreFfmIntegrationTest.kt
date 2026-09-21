// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.ServerSocket
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
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
