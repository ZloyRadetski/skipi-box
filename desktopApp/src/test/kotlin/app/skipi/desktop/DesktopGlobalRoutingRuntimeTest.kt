// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfileLibrary
import features.proxy.server.model.Custom
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import features.routing.model.RouteRule
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Global routing must reach only the generated no-profile Desktop start-plan branch. */
class DesktopGlobalRoutingRuntimeTest {
    @Test
    fun noProfilePlanUsesStrategyOrderedEnabledRulesAndTheirAdditionalServerTargets() = withXrayRoot { xrayRoot ->
        val serverLibrary = serverLibrary()
        val settings = settings(
            routeDomainStrategy = 2,
            routeRules = listOf(
                RouteRule(
                    id = 11,
                    remarks = "First global rule",
                    outboundTag = "20",
                    domain = listOf("first-route.example"),
                ),
                RouteRule(
                    id = 12,
                    remarks = "Disabled global rule",
                    outboundTag = "30",
                    domain = listOf("disabled-route.example"),
                    enabled = false,
                ),
                RouteRule(
                    id = 13,
                    remarks = "Second global rule",
                    outboundTag = "direct",
                    ip = listOf("203.0.113.7"),
                ),
            ),
        )
        val originalSettings = settings.copy(routeRules = settings.routeRules.toList())
        val originalServers = serverLibrary.copy(servers = serverLibrary.servers.toList())

        val plan = preparePlan(settings, serverLibrary, xrayRoot = xrayRoot)
        val root = configRoot(plan.configuration)
        val routing = root.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }
        val configuredRules = rules.filter { rule -> rule.containsKey("domain") || rule.containsKey("ip") }

        assertEquals("IPOnDemand", routing.getValue("domainStrategy").jsonPrimitive.content)
        assertEquals(
            listOf("first-route.example", "203.0.113.7"),
            configuredRules.mapNotNull { rule ->
                (rule["domain"] ?: rule["ip"])?.jsonArray?.singleOrNull()?.jsonPrimitive?.content
            },
        )
        assertEquals(
            listOf("20", "direct"),
            configuredRules.map { rule -> rule.getValue("outboundTag").jsonPrimitive.content },
        )
        assertEquals("proxy", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertTrue(outboundTags(root).contains("20"), "the rule's additional server outbound is missing")
        assertFalse(outboundTags(root).contains("30"), "a disabled rule must not add its server outbound")
        assertFalse(plan.configuration.contains("disabled-route.example"))
        assertFalse(plan.configuration.contains("geoip:private"))
        assertEquals(originalSettings, settings)
        assertEquals(originalServers, serverLibrary)
    }

    @Test
    fun noProfilePlanUsesAStoredServerAsTheFinalDefaultTarget() = withXrayRoot { xrayRoot ->
        val settings = settings(
            routeDomainStrategy = 1,
            defaultRouteOutboundTag = "20",
        )

        val plan = preparePlan(settings, serverLibrary(), xrayRoot = xrayRoot)
        val root = configRoot(plan.configuration)
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("20", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertTrue(outboundTags(root).contains("20"))
        assertTrue(plan.configuration.contains("secondary.example"))
    }

    @Test
    fun missingRuleAndDefaultTargetsFallBackToTheSelectedProxy() = withXrayRoot { xrayRoot ->
        val settings = settings(
            defaultRouteOutboundTag = "404",
            routeRules = listOf(
                RouteRule(
                    id = 14,
                    remarks = "Missing target",
                    outboundTag = "404",
                    domain = listOf("unresolved-target.example"),
                ),
            ),
        )

        val plan = preparePlan(settings, serverLibrary(), xrayRoot = xrayRoot)
        val root = configRoot(plan.configuration)
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("proxy", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertFalse(rules.any { rule ->
            rule["domain"]?.jsonArray?.any { value ->
                value.jsonPrimitive.content == "unresolved-target.example"
            } == true
        })
        assertFalse(outboundTags(root).contains("404"))
    }

    @Test
    fun activeProfileKeepsItsOwnRoutingDespiteConflictingGlobalSettings() = withXrayRoot { xrayRoot ->
        val profile = profile(
            id = 77,
            content = """
                [Rule]
                DOMAIN,profile-only.example,DIRECT
                FINAL,DIRECT
                [SKIPI]
                route-domain-strategy = AsIs
            """.trimIndent(),
        )
        val configLibrary = ConfigProfileLibrary(selectedConfigId = profile.id, configs = listOf(profile))
        val settings = settings(
            routeDomainStrategy = 2,
            defaultRouteOutboundTag = "20",
            routeRules = listOf(
                RouteRule(id = 15, outboundTag = "20", domain = listOf("global-only.example")),
            ),
        )

        val plan = preparePlan(settings, serverLibrary(), configLibrary, xrayRoot)
        val root = configRoot(plan.configuration)
        val routing = root.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("AsIs", routing.getValue("domainStrategy").jsonPrimitive.content)
        assertTrue(rules.any { rule ->
            rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "full:profile-only.example" } == true
        })
        assertFalse(plan.configuration.contains("global-only.example"))
        assertTrue(plan.assetsDirectory?.startsWith(xrayRoot.toPath()) == true)
    }

    @Test
    fun rawCustomKeepsItsRoutingAndPrecedenceOverProfileAndGlobalSettings() = withXrayRoot { xrayRoot ->
        val custom = Custom(
            remarks = "Raw custom",
            configJson = """
                {
                  "outbounds": [{ "tag": "raw-custom-out", "protocol": "freedom" }],
                  "routing": {
                    "domainStrategy": "AsIs",
                    "rules": [{
                      "type": "field",
                      "domain": ["custom-only.example"],
                      "outboundTag": "raw-custom-out"
                    }]
                  }
                }
            """.trimIndent(),
        )
        val serverLibrary = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(DesktopStoredProxyServer(10, custom.encodePersistedProxyServer())),
        )
        val profile = profile(
            id = 88,
            content = """
                [Rule]
                DOMAIN,profile-only.example,DIRECT
                FINAL,DIRECT
            """.trimIndent(),
        )
        val settings = settings(
            routeDomainStrategy = 2,
            defaultRouteOutboundTag = "20",
            routeRules = listOf(
                RouteRule(id = 16, outboundTag = "20", domain = listOf("global-only.example")),
            ),
        )

        val plan = preparePlan(
            settings = settings,
            serverLibrary = serverLibrary,
            configLibrary = ConfigProfileLibrary(selectedConfigId = profile.id, configs = listOf(profile)),
            xrayRoot = xrayRoot,
        )
        val root = configRoot(plan.configuration)
        val routing = root.getValue("routing").jsonObject

        assertEquals("AsIs", routing.getValue("domainStrategy").jsonPrimitive.content)
        assertEquals(
            "custom-only.example",
            routing.getValue("rules").jsonArray.single().jsonObject
                .getValue("domain").jsonArray.single().jsonPrimitive.content,
        )
        assertEquals(listOf("raw-custom-out"), outboundTags(root))
        assertFalse(plan.configuration.contains("profile-only.example"))
        assertFalse(plan.configuration.contains("global-only.example"))
        assertNotNull(plan.assetsDirectory)
    }

    private fun settings(
        routeDomainStrategy: Int = 0,
        defaultRouteOutboundTag: String = "proxy",
        routeRules: List<RouteRule> = emptyList(),
    ) = DesktopAppSettings(
        useWindowsSystemProxy = false,
        useSystemProxy = false,
        localProxyPort = 20_480,
        enableDynamicLocalProxyPort = true,
        routeDomainStrategy = routeDomainStrategy,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        routeRules = routeRules,
    )

    private fun serverLibrary() = DesktopServerLibrary(
        selectedServerId = 10,
        servers = listOf(
            storedSocks(10, "Selected server", "selected.example"),
            storedSocks(20, "Secondary server", "secondary.example"),
            storedSocks(30, "Disabled-rule server", "disabled.example"),
        ),
    )

    private fun storedSocks(id: Int, remarks: String, host: String) = DesktopStoredProxyServer(
        id = id,
        serverJson = Socks(remarks = remarks, server = host, port = "1080").encodePersistedProxyServer(),
    )

    private fun profile(id: Int, content: String) = DesktopStoredConfig(
        id = id,
        name = "Profile $id",
        content = content,
    )

    private fun preparePlan(
        settings: DesktopAppSettings,
        serverLibrary: DesktopServerLibrary,
        configLibrary: ConfigProfileLibrary = ConfigProfileLibrary(),
        xrayRoot: File,
    ): DesktopTunnelStartPlan = prepareDesktopTunnelStartPlan(
        settings = settings,
        serverLibrary = serverLibrary,
        configLibrary = configLibrary,
        latencyByServerId = emptyMap(),
        localProxyRuntime = DesktopLocalProxyRuntime(),
        resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot),
        allocatePort = { _, excludedPorts ->
            (20_480..20_490).first { candidate -> candidate !in excludedPorts }
        },
    )

    private fun configRoot(configuration: String) = Json.parseToJsonElement(configuration).jsonObject

    private fun outboundTags(root: JsonObject) = root.getValue("outbounds").jsonArray.map { outbound ->
        outbound.jsonObject.getValue("tag").jsonPrimitive.content
    }

    private inline fun withXrayRoot(block: (File) -> Unit) {
        val tempRoot = Files.createTempDirectory("skipi-global-routing-")
        val xrayRoot = tempRoot.resolve("xray").toFile().apply { mkdirs() }
        // Keep profile asset preparation isolated from the repository user's Xray data.
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
