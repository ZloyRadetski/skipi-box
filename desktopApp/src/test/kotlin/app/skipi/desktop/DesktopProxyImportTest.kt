// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.Custom
import features.proxy.server.model.VLESS
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopProxyImportTest {
    @Test
    fun plansXrayJsonObjectAsAndroidCompatibleCustomServer() {
        val plan = DesktopProxyImportPlanner.planText(
            """
                {
                  "remark": "Рабочий Xray",
                  "outbounds": [
                    {
                      "tag": "proxy",
                      "protocol": "vless",
                      "settings": {}
                    }
                  ]
                }
            """.trimIndent(),
        )

        val server = assertIs<Custom>(plan.servers.single())
        assertEquals("Рабочий Xray", server.remarks)
        assertTrue(server.configJson.contains("\n"))
        assertTrue(plan.diagnostics.isEmpty())
    }

    @Test
    fun plansXrayJsonArrayWithAndroidRemarksFallback() {
        val plan = DesktopProxyImportPlanner.planFile(
            fileName = "servers.json",
            text = """
                [
                  {
                    "remarks": "Первый",
                    "outbounds": [{ "tag": "proxy", "protocol": "vless", "settings": {} }]
                  },
                  {
                    "name": "Резервный",
                    "outbounds": [{ "tag": "proxy", "protocol": "trojan", "settings": {} }]
                  },
                  {
                    "outbounds": [{ "tag": "proxy", "protocol": "shadowsocks", "settings": {} }]
                  }
                ]
            """.trimIndent(),
        )

        val servers = plan.servers.map { server -> assertIs<Custom>(server) }
        assertEquals(
            listOf("Первый", "Резервный", "JSON (Shadowsocks) 3"),
            servers.map(Custom::remarks),
        )
        assertTrue(plan.diagnostics.isEmpty())
    }

    @Test
    fun reportsInvalidXrayJsonWithoutFallingBackToLinksImport() {
        val plan = DesktopProxyImportPlanner.planText("{ \"outbounds\": [ }")

        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.diagnostics.any { diagnostic ->
            diagnostic.code == DesktopProxyImportDiagnosticCode.InvalidJson &&
                diagnostic.severity == DesktopProxyImportDiagnosticSeverity.Error
        })
        assertTrue(plan.diagnostics.none { diagnostic ->
            diagnostic.code == DesktopProxyImportDiagnosticCode.UnsupportedFormat
        })
    }

    @Test
    fun plansMixedProxyLinksBase64SubscriptionAndManualSubscriptionWithoutDuplicates() {
        val first = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val second = "vless://123e4567-e89b-42d3-a456-426614174001@two.example.com:443?security=tls#Two"
        val base64 = Base64.getEncoder().encodeToString("$first\n$second".encodeToByteArray())
        assertEquals(2, DesktopMihomoPayloadImporter.import(base64).servers.size)

        val plan = DesktopProxyImportPlanner.planClipboard(
            text = "$first\n$first\n$base64\nhttps://subscription.example.com/profile?token=CaseSensitive\nhttps://subscription.example.com/profile?token=CaseSensitive",
        )

        assertEquals(2, plan.servers.size)
        assertTrue(plan.servers.all { it is VLESS })
        assertEquals(listOf("https://subscription.example.com/profile?token=CaseSensitive"), plan.subscriptions)
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateServers })
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateSubscriptions })
    }

    @Test
    fun plansShadowrocketConfAsConfigAndCarriesPortableMetadata() {
        val plan = DesktopProxyImportPlanner.planFile(
            fileName = "fallback.conf",
            text = """
                [SKIPI]
                profile-name = Desktop profile
                profile-update-url = https://profiles.example.com/desktop.conf
                profile-update-locked = true

                [General]
                ipv6 = false

                [Rule]
                FINAL,PROXY
            """.trimIndent(),
        )

        val config = assertIs<DesktopProxyImportAction.AddConfig>(plan.actions.single())
        assertEquals("Desktop profile", config.name)
        assertEquals("https://profiles.example.com/desktop.conf", config.sourceUrl)
        assertEquals(true, config.updateLocked)
        assertTrue(config.content.endsWith("\n"))
        assertTrue(plan.servers.isEmpty())
    }

    @Test
    fun treatsHttpProxyAsServerAndHttpsAsSubscription() {
        val plan = DesktopProxyImportPlanner.planText(
            """
                http://proxy-user:proxy-pass@proxy.example.com:8080
                https://provider.example.com/subscription
            """.trimIndent(),
        )

        assertEquals(1, plan.servers.size)
        assertEquals(listOf("https://provider.example.com/subscription"), plan.subscriptions)
    }

    @Test
    fun doesNotFetchMihomoProvidersAndReportsThemForTheCaller() {
        val plan = DesktopProxyImportPlanner.planText(
            """
                proxy-providers:
                  remote:
                    type: http
                    url: https://provider.example.com/nodes.yaml
            """.trimIndent(),
        )

        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.PendingMihomoProvider })
        assertTrue(plan.diagnostics.none { it.code == DesktopProxyImportDiagnosticCode.UnsupportedFormat })
    }

    @Test
    fun excludesItemsAlreadyKnownToTheLibraries() {
        val server = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val parsed = DesktopProxyImportPlanner.planText(server).servers.single()
        val plan = DesktopProxyImportPlanner.planText(
            text = "$server\nhttps://provider.example.com/subscription",
            existing = DesktopProxyImportExisting(
                serverFingerprints = setOf(parsed.connectionFingerprint()),
                subscriptionUrls = setOf("https://provider.example.com/subscription"),
            ),
        )

        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateServers })
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateSubscriptions })
    }

    @Test
    fun androidManualPolicyKeepsRepeatedServersAndUpdatesAnExistingSubscriptionOnce() {
        val server = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val parsed = DesktopProxyImportPlanner.planText(server).servers.single()
        val subscription = "https://provider.example.com/subscription"

        val plan = DesktopProxyImportPlanner.planText(
            text = "$server\n$server\n$subscription\n$subscription",
            existing = DesktopProxyImportExisting(
                serverFingerprints = setOf(parsed.connectionFingerprint()),
                subscriptionUrls = setOf(subscription),
                serverDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated,
                subscriptionDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated,
            ),
        )

        assertEquals(2, plan.servers.size)
        assertEquals(listOf(subscription), plan.subscriptions)
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateSubscriptions })
    }
}
