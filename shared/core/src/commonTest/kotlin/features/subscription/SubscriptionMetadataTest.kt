// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubscriptionMetadataTest {

    @Test
    fun parse_headers_with_provider_urls_and_userinfo() {
        val response = SubscriptionFetchResponse(
            body = "vless://uuid@server:443?security=tls#Server1",
            headers = mapOf(
                "profile-title" to "My VPN Service",
                "announce" to "Scheduled maintenance tomorrow",
                "announce-url" to "https://my-vpn.com/news/1",
                "support-url" to "https://t.me/my_vpn_bot",
                "support-email" to "support@my-vpn.com",
                "profile-web-page-url" to "https://my-vpn.com",
                "profile-update-interval" to "12",
                "subscription-userinfo" to "upload=1000;download=5000;total=10000000;expire=1735689600",
            ),
        )

        val metadata = response.subscriptionMetadata()

        assertEquals("My VPN Service", metadata.profileTitle)
        assertEquals("Scheduled maintenance tomorrow", metadata.announce)
        assertEquals("https://my-vpn.com/news/1", metadata.announceUrl)
        assertEquals("https://t.me/my_vpn_bot", metadata.supportUrl)
        assertEquals("support@my-vpn.com", metadata.supportEmail)
        assertEquals("https://my-vpn.com", metadata.profileWebPageUrl)
        assertEquals("12", metadata.profileUpdateIntervalHours)
        assertTrue(metadata.userInfoReceived)
        assertEquals(1000L, metadata.trafficUploadBytes)
        assertEquals(5000L, metadata.trafficDownloadBytes)
        assertEquals(10000000L, metadata.trafficTotalBytes)
        assertEquals(1735689600L, metadata.trafficExpireAtSeconds)
    }

    @Test
    fun parse_body_comments_as_fallback_when_headers_missing() {
        val body = """
            #profile-title: Fallback Provider
            #support-url: https://t.me/fallback_support
            #support-email: help@fallback.com
            #homepage: https://fallback-vpn.com
            #announce: Important server update
            #announce-url: https://fallback-vpn.com/alert
            #profile-update-interval: 6
            vless://uuid@server:443?security=tls#Node1
        """.trimIndent()

        val response = SubscriptionFetchResponse(
            body = body,
            headers = emptyMap(),
        )

        val metadata = response.subscriptionMetadata()

        assertEquals("Fallback Provider", metadata.profileTitle)
        assertEquals("https://t.me/fallback_support", metadata.supportUrl)
        assertEquals("help@fallback.com", metadata.supportEmail)
        assertEquals("https://fallback-vpn.com", metadata.profileWebPageUrl)
        assertEquals("Important server update", metadata.announce)
        assertEquals("https://fallback-vpn.com/alert", metadata.announceUrl)
        assertEquals("6", metadata.profileUpdateIntervalHours)
    }

    @Test
    fun parse_embedded_config_from_autorouting_header() {
        val response = SubscriptionFetchResponse(
            body = "vless://uuid@server:443#Node1",
            headers = mapOf(
                "autorouting" to "https://raw.githubusercontent.com/user/repo/main/profile.json",
            ),
        )

        val metadata = response.subscriptionMetadata()
        val embedded = assertNotNull(metadata.embeddedConfig)
        assertEquals("https://raw.githubusercontent.com/user/repo/main/profile.json", embedded.payload)
        assertTrue(embedded.isUrl)
        assertTrue(embedded.activate)
    }

    @Test
    fun parse_embedded_config_from_body_skipi_conf_links() {
        // Test onadd with URL
        val bodyOnAddUrl = """
            vless://uuid@server1:443#Node1
            skipi://conf/onadd/https://example.com/rules.conf
        """.trimIndent()
        val metadataOnAddUrl = SubscriptionFetchResponse(body = bodyOnAddUrl).subscriptionMetadata()
        val onAddUrl = assertNotNull(metadataOnAddUrl.embeddedConfig)
        assertEquals("https://example.com/rules.conf", onAddUrl.payload)
        assertTrue(onAddUrl.isUrl)
        assertTrue(onAddUrl.activate)

        // Test add with base64
        val bodyAddBase64 = """
            vless://uuid@server1:443#Node1
            skipi://conf/add/eyJOYW1lIjoiTXlDb25maWcifQ==
        """.trimIndent()
        val metadataAddBase64 = SubscriptionFetchResponse(body = bodyAddBase64).subscriptionMetadata()
        val addBase64 = assertNotNull(metadataAddBase64.embeddedConfig)
        assertEquals("eyJOYW1lIjoiTXlDb25maWcifQ==", addBase64.payload)
        assertFalse(addBase64.isUrl)
        assertFalse(addBase64.activate)

        // Test standard ://autorouting/onadd/ with URL
        val bodyAutoRouting = """
            vless://uuid@server1:443#Node1
            ://autorouting/onadd/https://raw.githubusercontent.com/user/repo/main/routing.json
        """.trimIndent()
        val metadataAutoRouting = SubscriptionFetchResponse(body = bodyAutoRouting).subscriptionMetadata()
        val autoRouting = assertNotNull(metadataAutoRouting.embeddedConfig)
        assertEquals("https://raw.githubusercontent.com/user/repo/main/routing.json", autoRouting.payload)
        assertTrue(autoRouting.isUrl)
        assertTrue(autoRouting.activate)
    }

    @Test
    fun parse_embedded_config_from_routing_header_with_direct_url() {
        val response = SubscriptionFetchResponse(
            body = "vless://uuid@server:443#Node1",
            headers = mapOf(
                "routing" to "https://example.com/configs/direct_routing.conf",
            ),
        )

        val metadata = response.subscriptionMetadata()
        val embedded = assertNotNull(metadata.embeddedConfig)
        assertEquals("https://example.com/configs/direct_routing.conf", embedded.payload)
        assertTrue(embedded.isUrl)
        assertTrue(embedded.activate)
    }
}
