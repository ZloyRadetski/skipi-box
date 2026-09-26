// SPDX-License-Identifier: GPL-3.0

package engine.xray

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
    fun rejectsUnknownPoliciesAndDesktopUnsupportedRules() {
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

        assertFailsWith<IllegalStateException> { plan("DOMAIN,example.com,Missing") }
        assertFailsWith<IllegalArgumentException> { plan("IP-ASN,13335,PROXY") }
        assertFailsWith<IllegalStateException> { plan("RULE-SET,https://example.com/rules,DIRECT") }
    }
}
