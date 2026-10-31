// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.config.ConfigProfileLibrary
import features.proxy.server.model.Custom
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import features.routing.model.RouteRule
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import platform.TunnelConnectRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Resource validation for generated global-routing Desktop starts. */
class DesktopGlobalRoutingResourceValidationTest {
    @Test
    fun noProfileUsesGlobalAssetsAndFiltersUnavailableGeoCriteriaButKeepsLiterals() = withTempRoot { root ->
        val globalAssets = root.resolve("global-assets").toFile().apply { mkdirs() }
        writeGeoDat(File(globalAssets, "geosite.dat"), "present-site")
        writeGeoDat(File(globalAssets, "geoip.dat"), "present-ip")
        writeGeoDat(File(globalAssets, "custom.dat"), "present-custom")
        val settings = settings(
            routeRules = listOf(
                RouteRule(
                    id = 1,
                    remarks = "present and literal criteria survive",
                    outboundTag = "direct",
                    domain = listOf(
                        "geosite:present-site",
                        "ext:custom.dat:present-custom",
                        "literal.example",
                        "geosite:missing-site",
                    ),
                    ip = listOf("geoip:present-ip", "203.0.113.8", "geoip:missing-ip"),
                ),
                RouteRule(
                    id = 2,
                    remarks = "geo-only missing criteria drop the whole rule",
                    outboundTag = "direct",
                    domain = listOf("geosite:missing-only"),
                    ip = listOf("geoip:missing-only"),
                ),
                RouteRule(
                    id = 3,
                    remarks = "literal criterion survives missing geo criterion",
                    outboundTag = "direct",
                    domain = listOf("geosite:missing-mixed", "retained-literal.example"),
                ),
            ),
        )

        val plan = prepareDesktopTunnelStartPlan(
            settings = settings,
            serverLibrary = serverLibrary(),
            configLibrary = ConfigProfileLibrary(),
            latencyByServerId = emptyMap(),
            localProxyRuntime = DesktopLocalProxyRuntime(),
            resourceFiles = DesktopProfileResourceFiles(xrayRootDir = root.resolve("xray").toFile()),
            globalAssetsDirectory = globalAssets.toPath(),
        )

        assertEquals(globalAssets.toPath(), plan.assetsDirectory)
        val configuredRules = configuredRules(plan.configuration)
        val presentRule = configuredRules.single { rule -> rule["domain"]?.jsonArray?.any { item ->
            item.jsonPrimitive.content == "literal.example"
        } == true }
        assertEquals(
            listOf("geosite:present-site", "ext:custom.dat:present-custom", "literal.example"),
            presentRule.getValue("domain").jsonArray.map { item -> item.jsonPrimitive.content },
        )
        assertEquals(
            listOf("geoip:present-ip", "203.0.113.8"),
            presentRule.getValue("ip").jsonArray.map { item -> item.jsonPrimitive.content },
        )

        val mixedRule = configuredRules.single { rule -> rule["domain"]?.jsonArray?.any { item ->
            item.jsonPrimitive.content == "retained-literal.example"
        } == true }
        assertEquals(listOf("retained-literal.example"), mixedRule.getValue("domain").jsonArray
            .map { item -> item.jsonPrimitive.content })
        assertFalse(plan.configuration.contains("missing-only"))
        assertFalse(plan.configuration.contains("missing-mixed"))
    }

    @Test
    fun enabledExternalDomainValidationKeepsAndroidErrorMessages() = withTempRoot { root ->
        val dataDirectory = root.resolve("global-assets").toFile().apply { mkdirs() }
        writeGeoDat(File(dataDirectory, "geosite.dat"), "present")

        val invalid = buildGeneratedConfig(
            routing = RoutingConfigRecord(
                rules = listOf(RouteRule(id = 1, domain = listOf("ext:bad"))),
            ),
            routingDataDirectory = dataDirectory.toPath(),
        )
        assertEquals(
            "Invalid external routing domain rule: ext:bad",
            assertNotNull(invalid).exceptionOrNull()?.message,
        )

        val missingFile = buildGeneratedConfig(
            routing = RoutingConfigRecord(
                rules = listOf(RouteRule(id = 2, domain = listOf("ext:missing.dat:corporate"))),
            ),
            routingDataDirectory = dataDirectory.toPath(),
        )
        assertEquals(
            "Missing external routing resource file: missing.dat",
            assertNotNull(missingFile).exceptionOrNull()?.message,
        )
    }

    @Test
    fun disabledExternalDomainRuleDoesNotRequireOrValidateItsResourceFile() = withTempRoot { root ->
        val dataDirectory = root.resolve("global-assets").toFile().apply { mkdirs() }
        val config = assertNotNull(
            buildGeneratedConfig(
                routing = RoutingConfigRecord(
                    rules = listOf(
                        RouteRule(
                            id = 9,
                            enabled = false,
                            domain = listOf("ext:missing.dat:corporate", "ext:malformed"),
                        ),
                    ),
                ),
                routingDataDirectory = dataDirectory.toPath(),
            ),
        ).getOrThrow()

        assertFalse(config.contains("ext:missing.dat:corporate"))
        assertFalse(config.contains("ext:malformed"))
    }

    @Test
    fun activeProfileAndRawCustomKeepTheirExistingRoutingAndAssetPrecedence() = withTempRoot { root ->
        val globalAssets = root.resolve("global-assets").toFile().apply { mkdirs() }
        writeGeoDat(File(globalAssets, "geosite.dat"), "global-tag")
        val xrayRoot = root.resolve("xray").toFile().apply { mkdirs() }
        listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat", "direct-cidr-v4.txt", "direct-cidr-v6.txt")
            .forEach { name -> File(xrayRoot, name).writeBytes(byteArrayOf(1, 2, 3)) }
        val profile = DesktopStoredConfig(
            id = 77,
            name = "Profile routing",
            content = "[Rule]\nDOMAIN,profile-only.example,DIRECT\nFINAL,DIRECT\n",
        )
        val globalSettings = settings(
            routeDomainStrategy = 2,
            routeRules = listOf(
                RouteRule(id = 11, domain = listOf("ext:malformed"), outboundTag = "direct"),
                RouteRule(id = 12, domain = listOf("global-only.example"), outboundTag = "direct"),
            ),
        )
        val profilePlan = prepareDesktopTunnelStartPlan(
            settings = globalSettings,
            serverLibrary = serverLibrary(),
            configLibrary = ConfigProfileLibrary(selectedConfigId = profile.id, configs = listOf(profile)),
            latencyByServerId = emptyMap(),
            localProxyRuntime = DesktopLocalProxyRuntime(),
            resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot),
            globalAssetsDirectory = globalAssets.toPath(),
        )

        assertEquals(xrayRoot.toPath().resolve("profiles/77"), profilePlan.assetsDirectory)
        assertTrue(profilePlan.configuration.contains("profile-only.example"))
        assertFalse(profilePlan.configuration.contains("global-only.example"))

        val custom = Custom(
            remarks = "Raw custom",
            configJson = """
                {
                  "outbounds": [{ "tag": "raw-custom-out", "protocol": "freedom" }],
                  "routing": { "rules": [{ "type": "field", "domain": ["custom-only.example"], "outboundTag": "raw-custom-out" }] }
                }
            """.trimIndent(),
        )
        val customConfig = assertNotNull(
            buildDesktopSelectedServerConfig(
                serverLibrary = DesktopServerLibrary(
                    selectedServerId = 8,
                    servers = listOf(DesktopStoredProxyServer(8, custom.encodePersistedProxyServer())),
                ),
                configLibrary = ConfigProfileLibrary(selectedConfigId = profile.id, configs = listOf(profile)),
                routing = globalSettings.toRoutingConfigRecord(),
                options = options(),
                trafficConfigContentById = mapOf(profile.id to profile.content),
                subscriptionPingUrl = "http://127.0.0.1:18086/probe",
                latencyByServerId = emptyMap(),
                routingDataDirectory = globalAssets.toPath(),
            ),
        ).getOrThrow()
        assertTrue(customConfig.contains("custom-only.example"))
        assertFalse(customConfig.contains("profile-only.example"))
        assertFalse(customConfig.contains("global-only.example"))
    }

    @Test
    fun dynamicPortRetryReusesTheCapturedGlobalAssetsDirectory() = withTempRoot { root ->
        val globalAssets = root.resolve("runtime-assets").toFile().apply { mkdirs() }
        writeGeoDat(File(globalAssets, "geosite.dat"), "retry-tag")
        writeGeoDat(File(globalAssets, "geoip.dat"), "retry-ip")
        val startRequests = mutableListOf<DesktopCoreStartRequest>()
        val settings = settings(dynamicPortEnabled = true)
        val plan = prepareDesktopTunnelStartPlan(
            settings = settings,
            serverLibrary = serverLibrary(),
            configLibrary = ConfigProfileLibrary(),
            latencyByServerId = emptyMap(),
            localProxyRuntime = DesktopLocalProxyRuntime(),
            resourceFiles = DesktopProfileResourceFiles(xrayRootDir = root.resolve("xray").toFile()),
            globalAssetsDirectory = globalAssets.toPath(),
            allocatePort = { _, excludedPorts ->
                (20_480..20_490).first { candidate -> candidate !in excludedPorts }
            },
        )
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
            startPlanForProfile = { Result.success(plan) },
            startCore = { request ->
                startRequests += request
                if (startRequests.size == 1) {
                    Result.failure(IllegalStateException("Core startup failed", IllegalArgumentException("Address already in use")))
                } else {
                    Result.success(DesktopCoreState(isRunning = true))
                }
            },
            stopCore = { Result.success(DesktopCoreState(isRunning = false)) },
            coreState = { DesktopCoreState(isRunning = false) },
        )

        val result = runBlocking { controller.connect(TunnelConnectRequest(profileId = "proxy-server-91")) }

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        assertEquals(2, startRequests.size)
        assertEquals(listOf(globalAssets.toPath(), globalAssets.toPath()), startRequests.map { it.assetsDirectory })
    }

    private fun buildGeneratedConfig(
        routing: RoutingConfigRecord,
        routingDataDirectory: Path?,
    ): Result<String>? = buildDesktopSelectedServerConfig(
        serverLibrary = serverLibrary(),
        configLibrary = ConfigProfileLibrary(),
        routing = routing,
        options = options(),
        trafficConfigContentById = emptyMap(),
        subscriptionPingUrl = "http://127.0.0.1:18086/probe",
        latencyByServerId = emptyMap(),
        routingDataDirectory = routingDataDirectory,
    )

    private fun settings(
        routeDomainStrategy: Int = 0,
        defaultRouteOutboundTag: String = "proxy",
        routeRules: List<RouteRule> = emptyList(),
        dynamicPortEnabled: Boolean = false,
    ) = DesktopAppSettings(
        localProxyPort = 20_480,
        useSystemProxy = false,
        useWindowsSystemProxy = false,
        enableDynamicLocalProxyPort = dynamicPortEnabled,
        routeDomainStrategy = routeDomainStrategy,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        routeRules = routeRules,
    )

    private fun serverLibrary() = DesktopServerLibrary(
        selectedServerId = 91,
        servers = listOf(
            DesktopStoredProxyServer(
                id = 91,
                serverJson = Socks(remarks = "Selected server", server = "selected.example", port = "1080")
                    .encodePersistedProxyServer(),
            ),
        ),
    )

    private fun options() = LocalProxyXrayConfigOptions(
        socksPort = 20_480,
        listenAddress = "127.0.0.1",
        logLevel = "none",
    )

    private fun configuredRules(configuration: String) = Json.parseToJsonElement(configuration)
        .jsonObject.getValue("routing").jsonObject.getValue("rules").jsonArray.map { value -> value.jsonObject }
        .filter { rule -> listOf("domain", "ip", "port", "network", "protocol", "process").any(rule::containsKey) }

    private fun writeGeoDat(file: File, vararg tags: String) {
        file.parentFile?.mkdirs()
        file.writeBytes(encodeGeoDat(*tags))
    }

    private fun encodeGeoDat(vararg tags: String): ByteArray = buildList {
        tags.forEach { tag ->
            val tagBytes = tag.encodeToByteArray()
            val entry = byteArrayOf(0x0a, tagBytes.size.toByte()) + tagBytes
            add(0x0a.toByte())
            add(entry.size.toByte())
            addAll(entry.asList())
        }
    }.toByteArray()

    private inline fun <T> withTempRoot(block: (Path) -> T): T {
        val root = Files.createTempDirectory("skipi-global-routing-resources-")
        return try {
            block(root)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
