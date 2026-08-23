// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.withActiveTrafficConfigApplied
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceRoscomvpnGithub
import features.subscription.SubscriptionEmbeddedConfig
import features.subscription.subscriptionMetadata
import features.subscription.runtime.SubscriptionFetchResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoscomRoutingJsonTest {

    private val releaseJson = """
        {
          "Name": "RoscomVPN",
          "GlobalProxy": "true",
          "UseChunkFiles": "true",
          "RemoteDns": "8.8.8.8",
          "DomesticDns": "77.88.8.8",
          "RemoteDNSType": "DoH",
          "RemoteDNSDomain": "https://8.8.8.8/dns-query",
          "RemoteDNSIP": "8.8.8.8",
          "DomesticDNSType": "DoH",
          "DomesticDNSDomain": "https://77.88.8.8/dns-query",
          "DomesticDNSIP": "77.88.8.8",
          "Geoipurl": "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geoip/release/geoip.dat",
          "Geositeurl": "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geosite/release/geosite.dat",
          "LastUpdated": "1787457545",
          "DnsHosts": {
            "lkfl2.nalog.ru": "213.24.64.175",
            "lknpd.nalog.ru": "213.24.64.181"
          },
          "RouteOrder": "block-proxy-direct",
          "DirectSites": [
            "geosite:private",
            "geosite:category-ru",
            "geosite:microsoft"
          ],
          "DirectIp": [
            "geoip:private",
            "geoip:direct"
          ],
          "ProxySites": [
            "geosite:google-play",
            "geosite:github",
            "geosite:youtube",
            "geosite:telegram"
          ],
          "ProxyIp": [],
          "BlockSites": [
            "geosite:win-spy",
            "geosite:torrent",
            "geosite:category-ads"
          ],
          "BlockIp": [],
          "DomainStrategy": "IPIfNonMatch",
          "FakeDNS": "false"
        }
    """.trimIndent()

    @Test
    fun detection_recognizes_routing_json_and_rejects_other_content() {
        assertNotNull(releaseJson.toRoscomRoutingJsonOrNull())

        // Plain JSON without routing keys is not ours.
        assertNull("""{"Name":"MyConfig"}""".toRoscomRoutingJsonOrNull())
        // Broken JSON and INI profiles are ignored.
        assertNull("{not json".toRoscomRoutingJsonOrNull())
        assertNull("[General]\ndns-server = 1.1.1.1".toRoscomRoutingJsonOrNull())
    }

    @Test
    fun conversion_produces_ordered_rules_and_skipi_settings() {
        val json = releaseJson.toRoscomRoutingJsonOrNull()
        assertNotNull(json)
        val conf = json!!.toRoscomRoutingShadowrocketConf(fallbackName = "Subscription Config")

        val rules = conf.analyzeShadowrocketConfig().rules
        val nonFinal = rules.filterNot { it.isFinal }

        // RouteOrder block-proxy-direct: block first, then proxy, then direct.
        assertEquals("DOMAIN-SET", nonFinal.first().type)
        assertEquals("geosite:win-spy", nonFinal.first().value)
        assertEquals("REJECT", nonFinal.first().policy)
        assertEquals("PROXY", nonFinal.first { it.value == "geosite:youtube" }.policy)
        assertEquals("DIRECT", nonFinal.first { it.value == "geosite:category-ru" }.policy)
        assertEquals("GEOIP", nonFinal.first { it.value == "private" }.type.takeIf { it == "GEOIP" })
        assertEquals("DIRECT", nonFinal.first { it.value == "private" }.policy)

        val final = rules.last()
        assertTrue(final.isFinal)
        assertEquals("FINAL,PROXY", "${final.type},${final.policy}")

        // SKIPI settings section carries DNS, hosts, FakeDNS and geo sources.
        assertTrue(conf.contains("profile-name = RoscomVPN"))
        assertTrue(conf.contains("proxy-dns = https://8.8.8.8/dns-query"))
        assertTrue(conf.contains("direct-dns = https://77.88.8.8/dns-query"))
        assertTrue(conf.contains("dns-hosts = lkfl2.nalog.ru:213.24.64.175"))
        assertTrue(conf.contains("dns-hosts = lknpd.nalog.ru:213.24.64.181"))
        assertTrue(conf.contains("fake-dns = false"))
        assertTrue(conf.contains("route-domain-strategy = IPIfNonMatch"))
    }

    @Test
    fun import_applies_converted_fields_to_state() {
        var state = AppState(
            trafficConfigs = emptyList(),
            activeTrafficConfigId = 0,
            nextTrafficConfigId = 1,
        )
        state = state.withImportedTrafficConfig(
            content = releaseJson,
            activate = true,
            fallbackName = "Subscription Config",
            sourceUrl = "subscription://1",
        )

        val config = state.trafficConfigs.single()
        assertEquals("RoscomVPN", config.name)
        assertEquals(config.id, state.activeTrafficConfigId)

        assertEquals(listOf("https://8.8.8.8/dns-query"), config.androidSettings.proxyDns)
        assertEquals(listOf("https://77.88.8.8/dns-query"), config.androidSettings.directDns)
        assertFalse(config.androidSettings.enableFakeDns)
        assertEquals(
            listOf("lkfl2.nalog.ru:213.24.64.175", "lknpd.nalog.ru:213.24.64.181"),
            config.androidSettings.dnsHosts,
        )
        // Provider DomainStrategy is respected instead of the AsIs default.
        assertEquals(1, config.androidSettings.routeDomainStrategy)
        // Release URLs match the built-in roscomvpn preset source.
        assertEquals(ResourceFileSourceRoscomvpnGithub, config.resourceSettings.source)

        val analysis = config.rawConfig.analyzeShadowrocketConfig()
        assertTrue(analysis.rules.any { it.type == "DOMAIN-SET" && it.value == "geosite:youtube" && it.policy == "PROXY" })
        assertTrue(analysis.rules.any { it.type == "GEOIP" && it.value == "direct" && it.policy == "DIRECT" })
    }

    @Test
    fun happ_base64_subscription_link_imports_end_to_end() {
        val body = """
            vless://uuid@server1:443#Node1
            $happLink
        """.trimIndent()

        val metadata = SubscriptionFetchResponse(body = body).subscriptionMetadata()
        val embedded = metadata.embeddedConfig
        assertNotNull(embedded)
        assertFalse(embedded!!.isUrl)
        assertTrue(embedded.activate)

        val decoded = embedded.payload.decodeSkipiPayload()
        assertNotNull(decoded)

        var state = AppState(
            trafficConfigs = emptyList(),
            activeTrafficConfigId = 0,
            nextTrafficConfigId = 1,
        )
        state = state.withImportedTrafficConfig(
            content = decoded!!,
            activate = true,
            fallbackName = "Subscription Config",
            sourceUrl = "subscription://7",
        )

        val config = state.trafficConfigs.single()
        assertEquals("RoscomVPN", config.name)
        // Versioned geo URLs do not match the release preset -> custom source.
        assertEquals(ResourceFileSourceCustom, config.resourceSettings.source)
        assertTrue(config.resourceSettings.customGeoIpUrl.contains("roscomvpn-geoip@202608230358"))
        assertTrue(config.androidSettings.proxyDns.contains("https://8.8.8.8/dns-query"))
    }

    @Test
    fun converted_domain_set_rules_reach_xray_route_rules() {
        val json = releaseJson.toRoscomRoutingJsonOrNull()
        assertNotNull(json)
        val conf = json!!.toRoscomRoutingShadowrocketConf(fallbackName = "Config")

        val state = AppState(
            trafficConfigs = listOf(
                TrafficConfigState(id = 1, name = "RoscomVPN", rawConfig = conf)
                    .withSkipiSettingsReadFromRawConfig(),
            ),
            activeTrafficConfigId = 1,
            nextRouteRuleId = 1,
        )
        val applied = state.withActiveTrafficConfigApplied()

        assertEquals(1, applied.routeDomainStrategy)
        val youtube = applied.routeRules.first { it.domain.contains("geosite:youtube") }
        assertEquals("proxy", youtube.outboundTag)
        val winSpy = applied.routeRules.first { it.domain.contains("geosite:win-spy") }
        assertEquals("block", winSpy.outboundTag)
        val privateIp = applied.routeRules.first { it.ip.contains("geoip:private") }
        assertEquals("direct", privateIp.outboundTag)
        assertEquals("proxy", applied.defaultRouteOutboundTag)
    }

    private companion object {
        // happ://routing/onadd/{base64} sample from a real provider; decodes to
        // the routing JSON with versioned geo resource URLs.
        const val HappLinkPrefix = "happ://routing/onadd/"
        val happLink: String
            get() = HappLinkPrefix + Base64Payload

        const val Base64Payload =
            "eyJOYW1lIjoiUm9zY29tVlBOIiwiR2xvYmFsUHJveHkiOiJ0cnVlIiwiVXNlQ2h1bmtGaWxlcyI6InRydWUiLCJSZW1vdGVEbnMiOiI4LjguOC44IiwiRG9tZXN0aWNEbnMiOiI3Ny44OC44LjgiLCJSZW1vdGVETlNUeXBlIjoiRG9IIiwiUmVtb3RlRE5TRG9tYWluIjoiaHR0cHM6Ly84LjguOC44L2Rucy1xdWVyeSIsIlJlbW90ZUROU0lQIjoiOC44LjguOCIsIkRvbWVzdGljRE5TVHlwZSI6IkRvSCIsIkRvbWVzdGljRE5TRG9tYWluIjoiaHR0cHM6Ly83Ny44OC44LjgvZG5zLXF1ZXJ5IiwiRG9tZXN0aWNETlNJUCI6Ijc3Ljg4LjguOCIsIkdlb2lwdXJsIjoiaHR0cHM6Ly9jZG4uanNkZWxpdnIubmV0L2doL2h5ZHJhcG9uaXF1ZS9yb3Njb212cG4tZ2VvaXBAMjAyNjA4MjMwMzU4L3JlbGVhc2UvZ2VvaXAuZGF0IiwiR2Vvc2l0ZXVybCI6Imh0dHBzOi8vY2RuLmpzZGVsaXZyLm5ldC9naC9oeWRyYXBvbmlxdWUvcm9zY29tdnBuLWdlb3NpdGVAMjAyNjA0MTUyMjM1L3JlbGVhc2UvZ2Vvc2l0ZS5kYXQiLCJMYXN0VXBkYXRlZCI6IjE3ODc0NTc1NDMiLCJEbnNIb3N0cyI6eyJsa2ZsMi5uYWxvZy5ydSI6IjIxMy4yNC42NC4xNzUiLCJsa25wZC5uYWxvZy5ydSI6IjIxMy4yNC42NC4xODEifSwiUm91dGVPcmRlciI6ImJsb2NrLXByb3h5LWRpcmVjdCIsIkRpcmVjdFNpdGVzIjpbImdlb3NpdGU6cHJpdmF0ZSIsImdlb3NpdGU6Y2F0ZWdvcnktcnUiLCJnZW9zaXRlOndoaXRlbGlzdCIsImdlb3NpdGU6bWljcm9zb2Z0IiwiZ2Vvc2l0ZTphcHBsZSIsImdlb3NpdGU6ZXBpY2dhbWVzIiwiZ2Vvc2l0ZTpyaW90IiwiZ2Vvc2l0ZTplc2NhcGVmcm9tdGFya292IiwiZ2Vvc2l0ZTpzdGVhbSIsImdlb3NpdGU6dHdpdGNoIiwiZ2Vvc2l0ZTpwaW50ZXJlc3QiLCJnZW9zaXRlOmZhY2VpdCJdLCJEaXJlY3RJcCI6WyJnZW9pcDpwcml2YXRlIiwiZ2VvaXA6ZGlyZWN0Il0sIlByb3h5U2l0ZXMiOlsiZ2Vvc2l0ZTpnb29nbGUtcGxheSIsImdlb3NpdGU6Z2l0aHViIiwiZ2Vvc2l0ZTp0d2l0Y2gtYWRzIiwiZ2Vvc2l0ZTp5b3V0dWJlIiwiZ2Vvc2l0ZTp0ZWxlZ3JhbSJdLCJQcm94eUlwIjpbXSwiQmxvY2tTaXRlcyI6WyJnZW9zaXRlOndpbi1zcHkiLCJnZW9zaXRlOnRvcnJlbnQiLCJnZW9zaXRlOmNhdGVnb3J5LWFkcyJdLCJCbG9ja0lwIjpbXSwiRG9tYWluU3RyYXRlZ3kiOiJJUElmTm9uTWF0Y2giLCJGYWtlRE5TIjoiZmFsc2UifQo="
    }
}