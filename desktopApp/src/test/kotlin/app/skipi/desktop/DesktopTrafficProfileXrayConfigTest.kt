// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.DefaultLocalHttpProxyListenAddress
import platform.LocalProxyXrayConfigOptions

class DesktopTrafficProfileXrayConfigTest {
    @Test
    fun compilesActiveProfileRulesDnsAndProxyGroupsIntoDesktopXrayJson() {
        val alpha = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val beta = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@beta.example:443#Beta",
        )
        val library = DesktopServerLibraries.select(
            DesktopServerLibraries.add(
                DesktopServerLibraries.add(DesktopServerLibrary(), alpha),
                beta,
            ),
            serverId = 1,
        )
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Routing",
            content = """
                [General]
                dns-server = 1.1.1.1,1.0.0.1
                ipv6 = true

                [SKIPI]
                route-domain-strategy = IPOnDemand

                [Host]
                profile.example = 203.0.113.9, 203.0.113.10

                [Proxy Group]
                Fast = url-test, Alpha, Beta, url=https://example.com/ping, interval=60

                [Rule]
                DOMAIN-SUFFIX,example.com,DIRECT
                DOMAIN,profile.example,Fast
                FINAL,Fast
            """.trimIndent() + "\n",
        )

        val config = DesktopTrafficProfileXrayConfigFactory.build(
            profile = profile,
            serverLibrary = library,
            options = LocalProxyXrayConfigOptions(socksPort = 20_480),
        )
        val root = Json.parseToJsonElement(config).jsonObject
        val inbound = root.getValue("inbounds").jsonArray.single().jsonObject
        val routing = root.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals(20_480, inbound.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("IPOnDemand", routing.getValue("domainStrategy").jsonPrimitive.content)
        assertEquals("1.1.1.1", root.getValue("dns").jsonObject.getValue("servers").jsonArray.first().jsonPrimitive.content)
        assertEquals("UseIP", root.getValue("dns").jsonObject.getValue("queryStrategy").jsonPrimitive.content)
        assertEquals(
            listOf("203.0.113.9", "203.0.113.10"),
            root.getValue("dns").jsonObject.getValue("hosts").jsonObject.getValue("profile.example").jsonArray
                .map { value -> value.jsonPrimitive.content },
        )
        assertTrue(rules.any { rule -> rule["outboundTag"]?.jsonPrimitive?.content == "direct" })
        assertTrue(rules.any { rule -> rule["balancerTag"]?.jsonPrimitive?.content?.startsWith("skipi-group-") == true })
        assertEquals(1, routing.getValue("balancers").jsonArray.size)
        assertTrue(root.containsKey("observatory"))
    }

    @Test
    fun addsHttpInboundForSystemProxyWithoutChangingSocksInbound() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Routing",
            content = "[Rule]\\nFINAL,PROXY\\n",
        )

        val root = Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = profile,
                serverLibrary = library,
                options = LocalProxyXrayConfigOptions(
                    socksPort = 20_480,
                    httpProxyPort = 20_481,
                    listenAddress = "0.0.0.0",
                    enableHttpProxy = true,
                ),
            ),
        ).jsonObject
        val inbounds = root.getValue("inbounds").jsonArray.map { it.jsonObject }

        assertEquals(2, inbounds.size)
        val socks = inbounds.first { inbound -> inbound.getValue("protocol").jsonPrimitive.content == "socks" }
        assertEquals("0.0.0.0", socks.getValue("listen").jsonPrimitive.content)
        val http = inbounds.first { inbound -> inbound.getValue("protocol").jsonPrimitive.content == "http" }
        assertEquals(20_481, http.getValue("port").jsonPrimitive.content.toInt())
        assertEquals(DefaultLocalHttpProxyListenAddress, http.getValue("listen").jsonPrimitive.content)
    }

    @Test
    fun rejectsTrafficProfileHttpInboundOutsideLoopback() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Routing",
            content = "[Rule]\\nFINAL,PROXY\\n",
        )

        assertFailsWith<IllegalArgumentException> {
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = profile,
                serverLibrary = library,
                options = LocalProxyXrayConfigOptions(
                    socksPort = 20_480,
                    httpProxyPort = 20_481,
                    httpProxyListenAddress = "0.0.0.0",
                    enableHttpProxy = true,
                ),
            )
        }
    }

    @Test
    fun resolvesProxyGroupMemberWhenSubscriptionRemarkHasCountryFlag() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@nl.example:443#%F0%9F%87%B3%F0%9F%87%B1%20Amsterdam",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Routing",
            content = "[Proxy Group]\\nEurope = select, Amsterdam\\n\\n[Rule]\\nFINAL,Europe\\n",
        )

        val root = Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(profile, library, LocalProxyXrayConfigOptions()),
        ).jsonObject
        val finalRule = root.getValue("routing").jsonObject.getValue("rules").jsonArray.last().jsonObject

        assertEquals("skipi-proxy", finalRule.getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun refusesProfilePolicyThatCannotBeResolvedToDesktopServerOrGroup() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Broken",
            content = "[Rule]\nDOMAIN,example.com,Missing\nFINAL,PROXY\n",
        )

        assertFailsWith<IllegalStateException> {
            DesktopTrafficProfileXrayConfigFactory.build(profile, library, LocalProxyXrayConfigOptions())
        }
    }

    @Test
    fun serializesProcessNameWithXrayProcessField() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Per process",
            content = "[Rule]\nPROCESS-NAME,firefox.exe,DIRECT\nFINAL,PROXY\n",
        )

        val root = Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(profile, library, LocalProxyXrayConfigOptions()),
        ).jsonObject
        val processRule = root.getValue("routing").jsonObject
            .getValue("rules").jsonArray
            .map { it.jsonObject }
            .first { "process" in it }

        assertEquals("firefox.exe", processRule.getValue("process").jsonArray.single().jsonPrimitive.content)
        assertFalse("processName" in processRule)
    }

    @Test
    fun preservesExplicitDomainPrefixInRuleSet() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(
            id = 1,
            name = "Explicit ruleset",
            content = "[Rule]\nRULE-SET,domain:api.example,DIRECT\nFINAL,PROXY\n",
        )

        val root = Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(profile, library, LocalProxyXrayConfigOptions()),
        ).jsonObject
        val domainRule = root.getValue("routing").jsonObject
            .getValue("rules").jsonArray
            .map { it.jsonObject }
            .first { "domain" in it }

        assertEquals("domain:api.example", domainRule.getValue("domain").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun failsFastForRulesThatNeedDesktopResourcesNotBundledWithXray() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)

        fun build(rule: String): String = DesktopTrafficProfileXrayConfigFactory.build(
            profile = DesktopStoredConfig(
                id = 1,
                name = "Unsupported rule",
                content = "[Rule]\n$rule\nFINAL,PROXY\n",
            ),
            serverLibrary = library,
            options = LocalProxyXrayConfigOptions(),
        )

        assertFailsWith<IllegalArgumentException> {
            build("IP-ASN,13335,PROXY")
        }
        assertFailsWith<IllegalArgumentException> {
            build("RULE-SET,ext:custom.dat:blocked,PROXY")
        }
    }
}
