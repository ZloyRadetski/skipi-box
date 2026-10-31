// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.LocalProxyStartRetryPolicy
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import platform.TunnelConnectRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun desktopRuntimeSnapshotStartsDynamicAuthenticatedInboundAndExposesItsFetchEndpoint() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        val localProxyRuntime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            useSystemProxy = false,
            useWindowsSystemProxy = false,
            enableDynamicLocalProxyPort = true,
            enableLocalProxyAuth = true,
            localProxyUsername = "dynamic-user",
            localProxyPassword = "dynamic-secret",
        )
        try {
            val options = localProxyRuntime.prepareStart(settings) { _, excludedPorts ->
                val port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
                    .use(ServerSocket::getLocalPort)
                check(port !in excludedPorts)
                port
            }
            val inbound = LocalProxyXrayConfigFactory.buildSocksInbound(options)
            val config = """
                {
                  "log": { "loglevel": "none" },
                  "inbounds": [$inbound],
                  "outbounds": [{ "protocol": "freedom", "tag": "direct" }]
                }
            """.trimIndent()

            localProxyRuntime.onCoreStarted(controller.start(config)).getOrThrow()
            assertTrue(controller.state().isRunning)
            assertEquals(
                DesktopSubscriptionSocksProxy(
                    host = "127.0.0.1",
                    port = options.socksPort,
                    username = "dynamic-user",
                    password = "dynamic-secret",
                ),
                localProxyRuntime.subscriptionFetchOptions(
                    settings = settings,
                    subscription = integrationSubscription(),
                    coreRunning = controller.state().isRunning,
                ).proxy,
            )

            connectWhenReady(options.socksPort).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                socket.soTimeout = 2_000

                output.write(byteArrayOf(5, 1, 2))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(2, input.readUnsignedByte())

                val username = "dynamic-user".encodeToByteArray()
                val password = "dynamic-secret".encodeToByteArray()
                output.writeByte(1)
                output.writeByte(username.size)
                output.write(username)
                output.writeByte(password.size)
                output.write(password)
                output.flush()

                assertEquals(1, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
            }

            localProxyRuntime.onCoreStopped(controller.stop()).getOrThrow()
            assertFalse(controller.state().isRunning)
            assertNull(localProxyRuntime.activeOptions(coreRunning = false))
        } finally {
            controller.close()
        }
    }

    @Test
    fun retriesNativeBindFailureWithExcludedPortAndPublishesOnlyTheAuthenticatedFinalEndpoint() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val coreController = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        val localProxyRuntime = DesktopLocalProxyRuntime()
        val blockedPortSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val blockedPort = blockedPortSocket.localPort
        val settings = DesktopAppSettings(
            useSystemProxy = false,
            useWindowsSystemProxy = false,
            enableDynamicLocalProxyPort = true,
            enableLocalProxyAuth = true,
            localProxyListenAddress = "127.0.0.1",
            localProxyUsername = "retry-user",
            localProxyPassword = "retry-secret",
        )
        val allocationExclusions = mutableListOf<Set<Int>>()
        var firstStartFailure: Throwable? = null
        var coreStartCalls = 0
        var planRebuildCalls = 0

        fun buildPlan(excludedPorts: Set<Int>): DesktopTunnelStartPlan {
            val options = localProxyRuntime.prepareStart(
                settings = settings,
                excludedPorts = excludedPorts,
                allocatePort = { listenAddress, excluded ->
                    allocationExclusions += excluded
                    if (allocationExclusions.size == 1) {
                        assertEquals(emptySet(), excluded)
                        blockedPort
                    } else {
                        assertTrue(blockedPort in excluded)
                        allocateUnexcludedLoopbackPort(listenAddress, excluded)
                    }
                },
            )
            val inbound = LocalProxyXrayConfigFactory.buildSocksInbound(options)
            val configuration = """
                {
                  "log": { "loglevel": "none" },
                  "inbounds": [$inbound],
                  "outbounds": [{ "protocol": "freedom", "tag": "direct" }]
                }
            """.trimIndent()
            return DesktopTunnelStartPlan(
                configuration = configuration,
                dynamicPortEnabled = settings.enableDynamicLocalProxyPort,
                localProxyPort = options.socksPort,
                rebuild = { failedPorts ->
                    planRebuildCalls += 1
                    buildPlan(failedPorts)
                },
            )
        }

        val tunnelController = DesktopTunnelController(
            configForProfile = { error("dynamic start plan must be used") },
            startPlanForProfile = { _ -> Result.success(buildPlan(emptySet())) },
            startCore = { request ->
                coreStartCalls += 1
                val result = coreController.start(request.configuration, request.assetsDirectory)
                if (coreStartCalls == 1) {
                    firstStartFailure = result.exceptionOrNull()
                    assertNotNull(firstStartFailure)
                    assertFalse(coreController.state().isRunning)
                    assertNull(localProxyRuntime.activeOptions(coreRunning = true))
                    blockedPortSocket.close()
                }
                localProxyRuntime.onCoreStarted(result)
            },
            stopCore = { localProxyRuntime.onCoreStopped(coreController.stop()) },
            coreState = { coreController.state() },
        )

        try {
            val connected = runBlocking {
                tunnelController.connect(TunnelConnectRequest("integration-profile"))
            }

            assertTrue(connected.isSuccess, connected.exceptionOrNull()?.toString())
            assertEquals(2, coreStartCalls)
            assertEquals(1, planRebuildCalls)
            assertEquals(listOf(emptySet(), setOf(blockedPort)), allocationExclusions)
            val bindFailure = assertNotNull(firstStartFailure)
            assertTrue(LocalProxyStartRetryPolicy.isLocalProxyBindFailure(bindFailure), bindFailure.toString())
            assertTrue(coreController.state().isRunning)

            val activeOptions = assertNotNull(localProxyRuntime.activeOptions(coreRunning = true))
            assertTrue(activeOptions.socksPort != blockedPort)
            assertEquals("retry-user", activeOptions.username)
            assertEquals("retry-secret", activeOptions.password)
            assertEquals(
                DesktopSubscriptionSocksProxy(
                    host = "127.0.0.1",
                    port = activeOptions.socksPort,
                    username = "retry-user",
                    password = "retry-secret",
                ),
                localProxyRuntime.subscriptionFetchOptions(
                    settings = settings,
                    subscription = integrationSubscription(),
                    coreRunning = true,
                ).proxy,
            )

            connectWhenReady(activeOptions.socksPort).use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                socket.soTimeout = 2_000
                output.write(byteArrayOf(5, 1, 2))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(2, input.readUnsignedByte())

                val username = "retry-user".encodeToByteArray()
                val password = "retry-secret".encodeToByteArray()
                output.writeByte(1)
                output.writeByte(username.size)
                output.write(username)
                output.writeByte(password.size)
                output.write(password)
                output.flush()
                assertEquals(1, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
            }

            assertTrue(runBlocking { tunnelController.disconnect() }.isSuccess)
            assertFalse(coreController.state().isRunning)
            assertNull(localProxyRuntime.activeOptions(coreRunning = false))
        } finally {
            blockedPortSocket.close()
            coreController.close()
        }
    }

    private fun integrationSubscription() = SubscriptionRecord(
        id = 1,
        title = "Integration",
        url = "https://sub.example/list",
        userAgent = "",
        updateInterval = "1d",
        hwid = "integration-install",
        ageSecretKey = "",
        updateViaProxy = true,
        autoOverrideRules = false,
        builtIn = false,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )

    private fun connectWhenReady(port: Int): Socket {
        repeat(100) {
            runCatching {
                return Socket("127.0.0.1", port)
            }
            Thread.sleep(10)
        }
        error("Native Core did not open its authenticated SOCKS listener on $port")
    }

    private fun allocateUnexcludedLoopbackPort(listenAddress: String, excludedPorts: Set<Int>): Int {
        repeat(32) {
            val port = ServerSocket(0, 0, InetAddress.getByName(listenAddress)).use(ServerSocket::getLocalPort)
            if (port !in excludedPorts) return port
        }
        error("Could not find an ephemeral loopback port outside $excludedPorts")
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
