// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoscomRoutingJsonTest {
    private val resourceSources = listOf(
        RoscomRoutingResourceSource(
            id = 4,
            geoIpUrl = "https://resources.example/geoip.dat",
            geoSiteUrl = "https://resources.example/geosite.dat",
        ),
    )

    @Test
    fun detectsAndConvertsPortableRoutingJson() {
        val routingJson = assertNotNull(
            """
            {
              "Name": "Shared routing",
              "GlobalProxy": "false",
              "RouteOrder": "block-proxy-direct",
              "BlockSites": ["geosite:ads"],
              "ProxySites": ["example.com"],
              "DirectIp": ["geoip:private", "10.0.0.0/8"],
              "RemoteDNSType": "DoH",
              "RemoteDNSDomain": "dns.example",
              "DomesticDNSType": "DoT",
              "DomesticDNSDomain": "dns.example:853",
              "DomesticDNSIP": "1.1.1.1",
              "Geoipurl": "https://resources.example/geoip.dat",
              "Geositeurl": "https://resources.example/geosite.dat",
              "DnsHosts": { "api.example": "203.0.113.10" },
              "DomainStrategy": "IPIfNonMatch"
            }
            """.trimIndent().toRoscomRoutingJsonOrNull(),
        )
        val conf = routingJson.toRoscomRoutingShadowrocketConf(
            fallbackName = "Fallback",
            resourceSources = resourceSources,
            customResourceSourceId = 5,
        )

        assertTrue(conf.contains("DOMAIN-SET,geosite:ads,REJECT"))
        assertTrue(conf.contains("DOMAIN-SUFFIX,example.com,PROXY"))
        assertTrue(conf.contains("GEOIP,private,DIRECT"))
        assertTrue(conf.contains("IP-CIDR,10.0.0.0/8,DIRECT"))
        assertTrue(conf.contains("FINAL,DIRECT"))
        assertTrue(conf.contains("proxy-dns = https://dns.example/dns-query"))
        assertTrue(conf.contains("direct-dns = 1.1.1.1"))
        assertTrue(conf.contains("dns-hosts = api.example:203.0.113.10"))
        assertTrue(conf.contains("route-domain-strategy = IPIfNonMatch"))
        assertTrue(conf.contains("resource-source = 4"))
    }

    @Test
    fun rejectsNonRoutingJson() {
        assertNull("{\"Name\":\"Not a routing profile\"}".toRoscomRoutingJsonOrNull())
        assertNull("[General]\ndns-server = 1.1.1.1".toRoscomRoutingJsonOrNull())
    }
}
