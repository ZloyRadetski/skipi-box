// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import features.resources.ResourceFileSourceRoscomvpnGithub
import features.resources.ResourceFileSourceV2FlyGithub
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.TunnelConnectRequest
import platform.TunnelConfiguration

class DesktopTunnelStartAssetsPlannerTest {
    @Test
    fun selectedConfigProfileAndItsRawResourceSourceSupplyTheCoreAssetsPath() = withXrayRoot { xrayRoot ->
        val active = storedProfile(
            id = 42,
            ruleDomain = "active-only.example",
            source = ResourceFileSourceRoscomvpnGithub,
        )
        val configLibrary = ConfigProfileLibrary(
            selectedConfigId = active.id,
            configs = listOf(active),
        )
        val plan = prepareDesktopTunnelStartPlan(
            settings = desktopSettings(),
            serverLibrary = testServerLibrary(),
            configLibrary = configLibrary,
            latencyByServerId = emptyMap(),
            localProxyRuntime = DesktopLocalProxyRuntime(),
            resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot),
        )
        var requestedProfileId: String? = null
        var started: DesktopCoreStartRequest? = null
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
            startPlanForProfile = { requestId ->
                requestedProfileId = requestId
                Result.success(plan)
            },
            startCore = { request ->
                started = request
                Result.success(DesktopCoreState(isRunning = true))
            },
            stopCore = { Result.success(DesktopCoreState(isRunning = false)) },
            coreState = { DesktopCoreState(isRunning = false) },
        )

        val result = runBlocking {
            controller.connect(TunnelConnectRequest(profileId = "proxy-server-91"))
        }

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        assertEquals("proxy-server-91", requestedProfileId)
        val startRequest = assertNotNull(started)
        assertEquals(xrayRoot.toPath().resolve("profiles/42"), startRequest.assetsDirectory)
        assertTrue(startRequest.configuration.contains("active-only.example"))
        assertFalse(startRequest.configuration.contains("proxy-server-91"))
        assertEquals(
            ResourceFileSourceRoscomvpnGithub.toString(),
            File(xrayRoot, "profiles/42/.resource-source").readText(),
        )
    }

    @Test
    fun dynamicPortRetryKeepsTheCapturedProfileConfigAndAssetsDirectory() = withXrayRoot { xrayRoot ->
        val firstProfile = storedProfile(
            id = 42,
            ruleDomain = "captured-only.example",
            source = ResourceFileSourceRoscomvpnGithub,
        )
        val secondProfile = storedProfile(
            id = 43,
            ruleDomain = "later-selection-only.example",
            source = ResourceFileSourceV2FlyGithub,
        )
        var latestConfigLibrary = libraryWith(firstProfile)
        val allocations = mutableListOf<Set<Int>>()
        val allocatedPorts = ArrayDeque(listOf(20_480, 20_481))
        val settings = desktopSettings(dynamicPortEnabled = true)
        val localProxyRuntime = DesktopLocalProxyRuntime()
        val resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot)
        val startRequests = mutableListOf<DesktopCoreStartRequest>()
        var running = false
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
            startPlanForProfile = { requestId ->
                assertEquals("proxy-server-91", requestId)
                Result.success(
                    prepareDesktopTunnelStartPlan(
                        settings = settings,
                        serverLibrary = testServerLibrary(),
                        configLibrary = latestConfigLibrary,
                        latencyByServerId = emptyMap(),
                        localProxyRuntime = localProxyRuntime,
                        resourceFiles = resourceFiles,
                        allocatePort = { _, excludedPorts ->
                            allocations += excludedPorts
                            allocatedPorts.removeFirst()
                        },
                    ),
                )
            },
            startCore = { request ->
                startRequests += request
                if (startRequests.size == 1) {
                    // A UI selection change during the failed start must not leak into its retry.
                    latestConfigLibrary = libraryWith(secondProfile)
                    Result.failure(
                        IllegalStateException(
                            "Core startup failed",
                            IllegalArgumentException("Address already in use"),
                        ),
                    )
                } else {
                    running = true
                    Result.success(DesktopCoreState(isRunning = true))
                }
            },
            stopCore = {
                running = false
                Result.success(DesktopCoreState(isRunning = false))
            },
            coreState = { DesktopCoreState(isRunning = running) },
        )

        val result = runBlocking {
            controller.connect(TunnelConnectRequest(profileId = "proxy-server-91"))
        }

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        assertEquals(43, latestConfigLibrary.selectedConfigId)
        assertEquals(listOf(emptySet(), setOf(20_480)), allocations)
        assertEquals(2, startRequests.size)
        val expectedAssetsDirectory = xrayRoot.toPath().resolve("profiles/42")
        assertEquals(
            listOf(expectedAssetsDirectory, expectedAssetsDirectory),
            startRequests.map { it.assetsDirectory },
        )
        assertEquals(listOf(20_480, 20_481), startRequests.map(::inboundSocksPort))
        startRequests.forEach { request ->
            assertTrue(request.configuration.contains("captured-only.example"))
            assertFalse(request.configuration.contains("later-selection-only.example"))
        }
        assertEquals(
            ResourceFileSourceRoscomvpnGithub.toString(),
            File(xrayRoot, "profiles/42/.resource-source").readText(),
        )
    }

    @Test
    fun unresolvedSelectedConfigIdFallsBackToSelectedServerWithoutPreparingAnyProfile() = withXrayRoot { xrayRoot ->
        val server = Socks(remarks = "Selected server", server = "selected.example", port = "1080")
        val selectedServerLibrary = DesktopServerLibrary(
            selectedServerId = 7,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 7,
                    serverJson = server.encodePersistedProxyServer(),
                ),
            ),
        )
        val unrelatedProfile = storedProfile(
            id = 42,
            ruleDomain = "unselected-profile-only.example",
            source = ResourceFileSourceRoscomvpnGithub,
        )

        val plan = prepareDesktopTunnelStartPlan(
            settings = desktopSettings(),
            serverLibrary = selectedServerLibrary,
            configLibrary = ConfigProfileLibrary(
                selectedConfigId = 999,
                configs = listOf(unrelatedProfile),
            ),
            latencyByServerId = emptyMap(),
            localProxyRuntime = DesktopLocalProxyRuntime(),
            resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot),
        )

        assertNull(plan.assetsDirectory)
        assertTrue(plan.configuration.contains("selected.example"))
        assertFalse(plan.configuration.contains("unselected-profile-only.example"))
        assertFalse(Files.exists(xrayRoot.toPath().resolve("profiles")))
    }

    @Test
    fun preparedConfigurationUsesGlobalAssetsFallbackWithoutPreparingAProfile() = runBlocking {
        var planCalls = 0
        var started: DesktopCoreStartRequest? = null
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("prepared config should be used")) },
            startPlanForProfile = {
                planCalls += 1
                Result.failure(AssertionError("prepared config must bypass profile preparation"))
            },
            startCore = { request ->
                started = request
                Result.success(DesktopCoreState(isRunning = true))
            },
            stopCore = { Result.success(DesktopCoreState(isRunning = false)) },
            coreState = { DesktopCoreState(isRunning = false) },
        )

        val result = controller.connect(
            TunnelConnectRequest(
                profileId = "proxy-server-91",
                configuration = TunnelConfiguration("prepared-configuration"),
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, planCalls)
        assertEquals("prepared-configuration", assertNotNull(started).configuration)
        assertNull(started?.assetsDirectory)
    }

    private fun desktopSettings(dynamicPortEnabled: Boolean = false) = DesktopAppSettings(
        useSystemProxy = false,
        useWindowsSystemProxy = false,
        localProxyPort = 20_480,
        enableDynamicLocalProxyPort = dynamicPortEnabled,
    )

    private fun libraryWith(profile: ConfigProfile) = ConfigProfileLibrary(
        selectedConfigId = profile.id,
        configs = listOf(profile),
    )

    private fun testServerLibrary(id: Int = 91) = DesktopServerLibrary(
        selectedServerId = id,
        servers = listOf(
            DesktopStoredProxyServer(
                id = id,
                serverJson = Socks(
                    remarks = "Selected server",
                    server = "selected.example",
                    port = "1080",
                ).encodePersistedProxyServer(),
            ),
        ),
    )

    private fun storedProfile(id: Int, ruleDomain: String, source: Int): ConfigProfile {
        val state = TrafficConfigState(
            id = id,
            name = "Profile $id",
            rawConfig = """
                [Rule]
                DOMAIN,$ruleDomain,DIRECT
                FINAL,DIRECT
            """.trimIndent(),
            resourceSettings = TrafficConfigResourceSettings(source = source),
        ).withSkipiSettingsInRawConfig()
        return ConfigProfile(id = id, name = state.name, content = state.rawConfig)
    }

    private fun inboundSocksPort(request: DesktopCoreStartRequest): Int = Json.parseToJsonElement(request.configuration)
        .jsonObject
        .getValue("inbounds")
        .jsonArray
        .single()
        .jsonObject
        .getValue("port")
        .jsonPrimitive
        .content
        .toInt()

    private inline fun withXrayRoot(block: (File) -> Unit) {
        val tempRoot = Files.createTempDirectory("skipi-desktop-start-assets-")
        val xrayRoot = tempRoot.resolve("xray").toFile().apply { mkdirs() }
        // The migration copies these legacy files into the first profile so the focused test does
        // not depend on package resources or emit a restore warning to the user's log directory.
        listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat", "direct-cidr-v4.txt", "direct-cidr-v6.txt")
            .forEach { name -> File(xrayRoot, name).writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            block(xrayRoot)
        } finally {
            Files.walk(tempRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
