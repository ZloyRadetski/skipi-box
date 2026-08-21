// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.SubscriptionGroupState
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdate
import features.proxy.server.usecase.ResolvedEmbeddedTrafficConfig
import features.proxy.server.usecase.subscriptionFetchIdentity
import features.proxy.server.usecase.withUpdatedSubscriptionServers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficConfigImportUpdateTest {

    @Test
    fun repeated_import_with_source_url_updates_existing_config() {
        val initialConfigText = """
            [General]
            ipv6 = true
            [Rule]
            FINAL,DIRECT
        """.trimIndent()

        val updatedConfigText = """
            [General]
            ipv6 = false
            [Rule]
            DOMAIN-SUFFIX,google.com,PROXY
            FINAL,DIRECT
        """.trimIndent()

        var state = AppState(
            trafficConfigs = emptyList(),
            activeTrafficConfigId = 0,
            nextTrafficConfigId = 1,
        )

        // 1. First import
        state = state.withImportedTrafficConfig(
            content = initialConfigText,
            activate = true,
            sourceUrl = "https://example.com/rules.conf",
            fallbackName = "Provider Config",
        )

        assertEquals(1, state.trafficConfigs.size)
        val config1 = state.trafficConfigs.first()
        assertEquals("https://example.com/rules.conf", config1.sourceUrl)
        assertEquals(config1.id, state.activeTrafficConfigId)
        assertTrue(config1.rawConfig.contains("ipv6 = true"))

        // 2. Second import with same sourceUrl
        state = state.withImportedTrafficConfig(
            content = updatedConfigText,
            activate = true,
            sourceUrl = "https://example.com/rules.conf",
            fallbackName = "Provider Config",
        )

        assertEquals("Should not create duplicate config", 1, state.trafficConfigs.size)
        val updatedConfig = state.trafficConfigs.first()
        assertEquals(config1.id, updatedConfig.id)
        assertEquals("https://example.com/rules.conf", updatedConfig.sourceUrl)
        assertTrue(updatedConfig.rawConfig.contains("ipv6 = false"))

        // 3. Third import via subscription update flow
        val group1 = SubscriptionGroupState(
            id = 1,
            name = "Provider",
            url = "https://sub.com",
            userAgent = "",
            updateInterval = "24",
            enabled = true,
        )
        state = state.copy(subscriptionGroups = listOf(group1))

        val update = ProxyServerListSubscriptionUpdate(
            groupId = 1,
            sourceIdentity = group1.subscriptionFetchIdentity(),
            urlCount = 1,
            servers = emptyList(),
            resolvedConfig = ResolvedEmbeddedTrafficConfig(
                content = updatedConfigText,
                sourceUrl = "https://example.com/rules.conf",
                fallbackName = "Provider Config",
                activate = true,
            ),
        )

        state = state.withUpdatedSubscriptionServers(
            updates = listOf(update),
            updatedAtMillis = System.currentTimeMillis(),
        )

        assertEquals("Should still have exactly 1 config after subscription update", 1, state.trafficConfigs.size)
    }

    @Test
    fun repeated_import_with_embedded_subscription_base64_updates_existing_config() {
        val configText1 = """
            [General]
            dns-server = 1.1.1.1
            [Rule]
            FINAL,DIRECT
        """.trimIndent()

        val configText2 = """
            [General]
            dns-server = 8.8.8.8
            [Rule]
            FINAL,DIRECT
        """.trimIndent()

        val group42 = SubscriptionGroupState(
            id = 42,
            name = "Sub 42",
            url = "https://sub42.com",
            userAgent = "",
            updateInterval = "24",
            enabled = true,
        )
        var state = AppState(
            subscriptionGroups = listOf(group42),
            trafficConfigs = emptyList(),
            activeTrafficConfigId = 0,
            nextTrafficConfigId = 1,
        )

        // First update from subscription group 42
        state = state.withUpdatedSubscriptionServers(
            updates = listOf(
                ProxyServerListSubscriptionUpdate(
                    groupId = 42,
                    sourceIdentity = group42.subscriptionFetchIdentity(),
                    urlCount = 1,
                    servers = emptyList(),
                    resolvedConfig = ResolvedEmbeddedTrafficConfig(
                        content = configText1,
                        sourceUrl = "subscription://42",
                        fallbackName = "Sub42 Config",
                        activate = true,
                    ),
                ),
            ),
            updatedAtMillis = 1000L,
        )

        assertEquals(1, state.trafficConfigs.size)
        val imported = state.trafficConfigs.first()
        assertEquals("subscription://42", imported.sourceUrl)

        // Second update from subscription group 42
        state = state.withUpdatedSubscriptionServers(
            updates = listOf(
                ProxyServerListSubscriptionUpdate(
                    groupId = 42,
                    sourceIdentity = group42.subscriptionFetchIdentity(),
                    urlCount = 1,
                    servers = emptyList(),
                    resolvedConfig = ResolvedEmbeddedTrafficConfig(
                        content = configText2,
                        sourceUrl = "subscription://42",
                        fallbackName = "Sub42 Config",
                        activate = true,
                    ),
                ),
            ),
            updatedAtMillis = 2000L,
        )

        assertEquals("Must reuse the existing config rather than create a duplicate", 1, state.trafficConfigs.size)
        assertEquals(imported.id, state.trafficConfigs.first().id)
    }
}
