// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyServerCopyTextTest {

    @Test
    fun copiesCleanVlessXrayJsonWithoutAndroidVpnPollution() = runBlocking {
        val vless = VLESS(
            remarks = "Test Node",
            id = "a3f5c760-705b-43ad-8d39-e9389e6eb5c2",
            server = "vpn.example.com",
            port = "443",
        )
        val serverState = ProxyServerState(id = 1, server = vless, groupId = 0)
        val appState = AppState(
            proxyServers = listOf(serverState),
            selectedProxyServerId = 1,
            tunVpnDns = "172.19.0.2",
            enableFakeDns = true,
            routeRules = listOf(
                RouteRule(
                    id = 10,
                    remarks = "Leaked App Route",
                    outboundTag = "direct",
                    domain = listOf("example.org"),
                    enabled = true,
                ),
            ),
        )

        val result = serverState.proxyServerCopyText(
            context = null,
            appState = appState,
            type = ProxyServerCopyTextType.FullJson,
        )

        assertTrue(result is ProxyServerCopyTextResult.Success)
        val jsonText = (result as ProxyServerCopyTextResult.Success).text
        val root = Json.parseToJsonElement(jsonText).jsonObject

        // 1. Inbounds: must have standard SOCKS and HTTP, and NO tun or dns-in
        val inbounds = root.getValue("inbounds").jsonArray.map { it.jsonObject }
        assertEquals(2, inbounds.size)
        val inProtocols = inbounds.map { it.getValue("protocol").jsonPrimitive.content }
        assertTrue(inProtocols.contains("socks"))
        assertTrue(inProtocols.contains("http"))
        val inTags = inbounds.map { it.getValue("tag").jsonPrimitive.content }
        assertFalse(inTags.contains("tun"))
        assertFalse(inTags.contains("dns-in"))

        // 2. DNS: must have public resolvers, NO 172.19.0.2, NO fakeDns
        val dns = root.getValue("dns").jsonObject
        val dnsServers = dns.getValue("servers").jsonArray.map { it.jsonPrimitive.content }
        assertTrue(dnsServers.contains("1.1.1.1"))
        assertFalse(dnsServers.contains("172.19.0.2"))
        assertNull(dns["tag"])
        assertNull(root["fakeDns"])

        // 3. Routing: clean rules, no leaked app rules, no dns-in or direct-dns rules
        val routing = root.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }
        assertFalse(rules.any { rule ->
            rule["inboundTag"]?.jsonPrimitive?.content == "dns-in" ||
                rule["inboundTag"]?.jsonPrimitive?.content == "direct-dns" ||
                rule["inboundTag"]?.jsonPrimitive?.content == "proxy-dns"
        })
        assertFalse(rules.any { rule ->
            rule["domain"]?.toString()?.contains("example.org") == true
        })
        assertTrue(rules.any { rule ->
            rule["outboundTag"]?.jsonPrimitive?.content == "direct" &&
                rule["ip"]?.jsonArray?.any { it.jsonPrimitive.content == "geoip:private" } == true
        })
        val finalRule = rules.last()
        assertEquals("proxy", finalRule["outboundTag"]?.jsonPrimitive?.content)

        // 4. Log: no Android device paths
        val log = root.getValue("log").jsonObject
        assertEquals("warning", log["loglevel"]?.jsonPrimitive?.content)
        assertNull(log["access"])
        assertNull(log["error"])
    }

    @Test
    fun copiesStrategyGroupWithBalancersAndObservatory() = runBlocking {
        val node1 = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "Node 1", id = "uuid1", server = "s1.com", port = "443"),
            groupId = 0,
        )
        val node2 = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Node 2", id = "uuid2", server = "s2.com", port = "443"),
            groupId = 0,
        )
        val group = StrategyGroup(
            remarks = "Balancer",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(10, 20),
            probeUrl = "https://www.gstatic.com/generate_204",
            probeInterval = "30s",
        )
        val groupState = ProxyServerState(id = 100, server = group, groupId = 0)
        val appState = AppState(
            proxyServers = listOf(node1, node2, groupState),
            selectedProxyServerId = 100,
        )

        val result = groupState.proxyServerCopyText(
            context = null,
            appState = appState,
            type = ProxyServerCopyTextType.FullJson,
        )

        assertTrue(result is ProxyServerCopyTextResult.Success)
        val jsonText = (result as ProxyServerCopyTextResult.Success).text
        val root = Json.parseToJsonElement(jsonText).jsonObject

        // Balancers must be present
        val routing = root.getValue("routing").jsonObject
        val balancers = routing.getValue("balancers").jsonArray.map { it.jsonObject }
        assertEquals(1, balancers.size)
        assertEquals("leastPing", balancers.first()["strategy"]?.jsonObject?.get("type")?.jsonPrimitive?.content)

        // Observatory must be present
        assertNotNull(root["observatory"])
        assertEquals(
            "https://www.gstatic.com/generate_204",
            root["observatory"]?.jsonObject?.get("probeURL")?.jsonPrimitive?.content,
        )

        // Final routing rule must target the balancer
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }
        val finalRule = rules.last()
        assertEquals(balancers.first()["tag"]?.jsonPrimitive?.content, finalRule["balancerTag"]?.jsonPrimitive?.content)
    }

    @Test
    fun copiesCustomServerWithCleanInbounds() = runBlocking {
        val customConfig = """
            {
                "outbounds": [
                    {
                        "tag": "custom-proxy",
                        "protocol": "vmess",
                        "settings": {
                            "vnext": [
                                {
                                    "address": "custom.example.com",
                                    "port": 443,
                                    "users": [{ "id": "b831381d-6324-4d53-ad4f-8cda48b30811" }]
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()
        val custom = Custom(remarks = "My Custom", configJson = customConfig, overrideInboundAndDns = true)
        val customState = ProxyServerState(id = 5, server = custom, groupId = 0)
        val appState = AppState(proxyServers = listOf(customState), selectedProxyServerId = 5)

        val result = customState.proxyServerCopyText(
            context = null,
            appState = appState,
            type = ProxyServerCopyTextType.FullJson,
        )

        assertTrue(result is ProxyServerCopyTextResult.Success)
        val jsonText = (result as ProxyServerCopyTextResult.Success).text
        val root = Json.parseToJsonElement(jsonText).jsonObject

        val inbounds = root.getValue("inbounds").jsonArray.map { it.jsonObject }
        assertEquals(2, inbounds.size)
        assertEquals("warning", root.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
    }

    @Test
    fun urlCopyActionReturnsUrlForUrlProxyServer() = runBlocking {
        val vless = VLESS(
            remarks = "Test",
            id = "a3f5c760-705b-43ad-8d39-e9389e6eb5c2",
            server = "vpn.example.com",
            port = "443",
        )
        val serverState = ProxyServerState(id = 1, server = vless, groupId = 0)
        val appState = AppState(proxyServers = listOf(serverState), selectedProxyServerId = 1)

        val result = serverState.proxyServerCopyText(
            context = null,
            appState = appState,
            type = ProxyServerCopyTextType.Url,
        )

        assertTrue(result is ProxyServerCopyTextResult.Success)
        val url = (result as ProxyServerCopyTextResult.Success).text
        assertTrue(url.startsWith("vless://"))
    }
}
