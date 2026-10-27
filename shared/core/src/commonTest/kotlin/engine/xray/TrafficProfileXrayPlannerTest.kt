// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.config.TrafficConfigReference
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrafficProfileXrayPlannerTest {
    @Test
    fun resolvesServerAndRecursiveConfigFinalPoliciesWithProxyFallbackOnMissingAndCycles() {
        val alpha = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        val beta = TrafficProfileServerRecord(
            2,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@beta.example:443#Beta"),
        )

        val plan = TrafficProfileXrayPlanner.plan(
            profileContent = """
                [Rule]
                DOMAIN,server.example,Beta
                DOMAIN,recursive.example,CONFIG:2
                DOMAIN,cycle.example,CONFIG:4
                DOMAIN,missing.example,CONFIG:99
                FINAL,CONFIG:2
            """.trimIndent(),
            servers = listOf(alpha, beta),
            selectedServerId = alpha.id,
            configs = listOf(
                TrafficConfigReference(2, "[Rule]\nFINAL,CONFIG:3"),
                TrafficConfigReference(3, "[Rule]\nFINAL,REJECT-DROP"),
                TrafficConfigReference(4, "[Rule]\nFINAL,CONFIG:4"),
            ),
        )

        assertEquals(
            listOf("skipi-server-2", "block", "skipi-proxy", "skipi-proxy", "block"),
            plan.routingRules.map { rule -> rule.getValue("outboundTag").jsonPrimitive.content },
        )
    }

    @Test
    fun groupNamePrecedesBuiltinPolicyWhenResolvingDesktopRouteTargets() {
        val alpha = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        val beta = TrafficProfileServerRecord(
            2,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@beta.example:443#Beta"),
        )

        val plan = TrafficProfileXrayPlanner.plan(
            profileContent = """
                [Proxy Group]
                PROXY = select, Beta

                [Rule]
                DOMAIN,group.example,PROXY
                FINAL,PROXY
            """.trimIndent(),
            servers = listOf(alpha, beta),
            selectedServerId = alpha.id,
        )

        assertEquals(
            listOf("skipi-server-2", "skipi-server-2"),
            plan.routingRules.map { rule -> rule.getValue("outboundTag").jsonPrimitive.content },
        )
    }

    @Test
    fun resolvesLocalProxyGroupsAndCompilesTheirRoutingTargets() {
        val alpha = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        val beta = TrafficProfileServerRecord(
            2,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@beta.example:443#Beta"),
        )
        val plan = TrafficProfileXrayPlanner.plan(
            profileContent = """
                [Proxy Group]
                Fast = url-test, Alpha, Beta, url=https://example.com/ping, interval=60

                [Rule]
                PROCESS-NAME,firefox.exe,DIRECT
                FINAL,Fast
            """.trimIndent(),
            servers = listOf(alpha, beta),
            selectedServerId = alpha.id,
        )

        assertEquals("skipi-proxy", plan.proxyOutbounds.first().getValue("tag").jsonPrimitive.content)
        assertEquals(1, plan.balancers.size)
        assertEquals("60s", plan.observatoryInterval)
        assertTrue(plan.routingRules.any { rule -> rule["balancerTag"]?.jsonPrimitive?.content == "skipi-group-1" })
        val processRule = plan.routingRules.first { "process" in it }
        assertEquals("firefox.exe", processRule.getValue("process").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun resolvesUnknownPoliciesToAndroidOutboundTags() {
        val server = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        val plan = TrafficProfileXrayPlanner.plan(
            profileContent = """
                [Rule]
                DOMAIN,example.com,shadowrocket-group:custom-route
                FINAL,custom-final
            """.trimIndent(),
            servers = listOf(server),
            selectedServerId = server.id,
        )

        assertEquals(
            listOf("custom-route", "custom-final"),
            plan.routingRules.map { rule -> rule.getValue("outboundTag").jsonPrimitive.content },
        )
    }

    @Test
    fun rejectsDesktopUnsupportedRules() {
        val server = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        fun plan(rule: String) = TrafficProfileXrayPlanner.plan(
            profileContent = "[Rule]\n$rule\nFINAL,PROXY\n",
            servers = listOf(server),
            selectedServerId = server.id,
            capabilities = TrafficProfileXrayCapabilities(
                supportsIpAsnRules = false,
                unsupportedIpAsnMessage = { value -> "Unsupported IP-ASN: $value" },
                externalRuleSetReferenceMessage = { value -> "No external rules: $value" },
                unsupportedExternalRuleSetMessage = { source, data -> "Unsupported $data file: $source" },
            ),
        )

        assertFailsWith<IllegalArgumentException> { plan("IP-ASN,13335,PROXY") }
    }

    @Test
    fun omittedUrlRuleDoesNotResolveItsUnknownPolicy() {
        val server = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        val result = TrafficProfileXrayPlanner.plan(
            profileContent = "[Rule]\nRULE-SET,https://example.com/rules,Missing\nFINAL,PROXY\n",
            servers = listOf(server),
            selectedServerId = server.id,
            capabilities = TrafficProfileXrayCapabilities(),
        )

        // Android's converter returns null before asking its caller to resolve a policy.
        assertEquals(1, result.routingRules.size) // The remaining rule is Desktop's FINAL catch-all.
    }

    @Test
    fun domainRuleWithExtLookingValueIsNotAnExternalResourceReference() {
        val server = TrafficProfileServerRecord(
            1,
            ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
        )
        val result = TrafficProfileXrayPlanner.plan(
            profileContent = "[Rule]\nDOMAIN,ext:custom.dat:blocked,DIRECT\nFINAL,PROXY\n",
            servers = listOf(server),
            selectedServerId = server.id,
        )
        val domainRule = result.routingRules.first { "domain" in it }

        assertEquals(
            "full:ext:custom.dat:blocked",
            domainRule.getValue("domain").jsonArray.single().jsonPrimitive.content,
        )
    }
}
