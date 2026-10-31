// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.Custom
import features.proxy.server.model.Socks
import features.proxy.server.model.VLESS
import features.proxy.server.model.Wireguard
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
    fun reportsJsonArraysWithoutConfigObjectsAsInvalidForTextAndFileSources() {
        listOf("[]", "[\"metadata\"]").forEach { json ->
            listOf(
                DesktopProxyImportPlanner.planText(json),
                DesktopProxyImportPlanner.planFile("servers.json", json),
            ).forEach { plan ->
                assertTrue(plan.actions.none { action -> action is DesktopProxyImportAction.AddConfig })
                assertTrue(plan.servers.isEmpty())
                assertTrue(plan.diagnostics.any { diagnostic ->
                    diagnostic.code == DesktopProxyImportDiagnosticCode.InvalidConfig &&
                        diagnostic.severity == DesktopProxyImportDiagnosticSeverity.Error
                })
            }
        }
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
    fun plansClipboardPayloadWithAndroidSourceDecodingAndDuplicateSemantics() {
        val first = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val second = "vless://123e4567-e89b-42d3-a456-426614174001@two.example.com:443?security=tls#Two"
        val subscription = "https://subscription.example.com/profile?token=CaseSensitive"
        val base64 = Base64.getEncoder().encodeToString("$first\n$second".encodeToByteArray())
        assertEquals(2, DesktopMihomoPayloadImporter.import(base64).servers.size)

        val plan = DesktopProxyImportPlanner.planClipboard(
            text = "$first\n$first\n$base64\n$subscription\n$subscription",
            existing = DesktopProxyImportExisting(
                serverDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated,
                subscriptionDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated,
            ),
        )

        // Android's URL parser sees the same literal link only once. Clipboard
        // imports do not decode the adjacent Base64 line.
        assertEquals(1, plan.servers.size)
        assertTrue(plan.servers.all { it is VLESS })
        assertEquals(listOf(subscription), plan.subscriptions)
        assertTrue(plan.diagnostics.none { it.code == DesktopProxyImportDiagnosticCode.DuplicateServers })
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateSubscriptions })
    }

    @Test
    fun clipboardDoesNotDecodeBase64ProxyPayloads() {
        val server = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val base64 = Base64.getEncoder().encodeToString(server.encodeToByteArray())

        val plan = DesktopProxyImportPlanner.planClipboard(base64)

        assertTrue(plan.servers.isEmpty())
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.UnsupportedFormat })
    }

    @Test
    fun fileImportAcceptsBase64ProxyPayloads() {
        val first = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val second = "vless://123e4567-e89b-42d3-a456-426614174001@two.example.com:443?security=tls#Two"
        val base64 = Base64.getEncoder().encodeToString("$first\n$second".encodeToByteArray())

        val plan = DesktopProxyImportPlanner.planFile("servers.txt", base64)

        assertEquals(listOf("One", "Two"), plan.servers.map { server -> server.getInfo().remarks })
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
    fun keepsProxyUrlsInsideShadowrocketConfAsPartOfTheProfile() {
        val raw = """
            [General]
            ipv6 = false

            [Proxy]
            # Example server: vless://123e4567-e89b-42d3-a456-426614174001@url.example.com:443?security=tls#URL

            [Rule]
            FINAL,PROXY
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planFile("profile.conf", raw)

        val config = assertIs<DesktopProxyImportAction.AddConfig>(plan.actions.single())
        assertEquals("profile", config.name)
        assertEquals("$raw\n", config.content)
        assertTrue(plan.servers.isEmpty())
        assertTrue(plan.diagnostics.isEmpty())
    }

    @Test
    fun keepsProxyUrlsInsideRecognizedShadowrocketTextAsPartOfTheProfile() {
        val raw = """
            [General]
            ipv6 = false

            [Proxy]
            # Example server: vless://123e4567-e89b-42d3-a456-426614174001@url.example.com:443?security=tls#URL

            [Rule]
            FINAL,PROXY
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planText(raw)

        val config = assertIs<DesktopProxyImportAction.AddConfig>(plan.actions.single())
        assertEquals("Импортированный конфиг", config.name)
        assertEquals("$raw\n", config.content)
        assertTrue(plan.servers.isEmpty())
        assertTrue(plan.diagnostics.isEmpty())
    }

    @Test
    fun importsWireguardConfThroughSharedParserBeforeShadowrocketConfHandling() {
        val plan = DesktopProxyImportPlanner.planFile(
            fileName = "tunnel.conf",
            text = """
                # Desktop tunnel
                [Interface]
                PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
                Address = 10.0.0.2/32

                [Peer]
                PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
                Endpoint = 203.0.113.1:51820
            """.trimIndent(),
        )

        val server = assertIs<Wireguard>(plan.servers.single())
        assertEquals("Desktop tunnel", server.remarks)
        assertEquals("203.0.113.1", server.server)
        assertTrue(plan.diagnostics.isEmpty())
    }

    @Test
    fun prefersMihomoYamlOverWireguardAndProxyUrlTextInTheSameFile() {
        val embeddedWireguard = """
            proxies:
              - name: YAML node
                type: socks5
                server: yaml.example.com
                port: 1080
            wireguard-sample: |
              [Interface]
              PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
              Address = 10.0.0.2/32
              [Peer]
              PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
              Endpoint = 203.0.113.1:51820
            # vless://123e4567-e89b-42d3-a456-426614174001@url.example.com:443?security=tls#URL
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planFile("servers.yaml", embeddedWireguard)

        val server = assertIs<Socks>(plan.servers.single())
        assertEquals("YAML node", server.remarks)
        assertEquals("yaml.example.com", server.server)
        assertEquals("1080", server.port)
    }

    @Test
    fun prefersCustomJsonOverYamlAndEmbeddedProxyUrls() {
        val plan = DesktopProxyImportPlanner.planText(
            """
                {
                  "remark": "Xray JSON",
                  "proxies": [{ "name": "YAML node", "type": "socks5", "server": "yaml.example.com", "port": 1080 }],
                  "proxyUrl": "vless://123e4567-e89b-42d3-a456-426614174001@url.example.com:443?security=tls#URL",
                  "outbounds": [{ "tag": "proxy", "protocol": "vless", "settings": {} }]
                }
            """.trimIndent(),
        )

        val server = assertIs<Custom>(plan.servers.single())
        assertEquals("Xray JSON", server.remarks)
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
    fun androidManualPolicyDeduplicatesRepeatedLiteralLinksAndKeepsDistinctLinksForOneConnection() {
        val server = "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#One"
        val differentLiteralForSameConnection =
            "vless://123e4567-e89b-42d3-a456-426614174000@one.example.com:443?security=tls#Alternate"
        val parsed = DesktopProxyImportPlanner.planText(server).servers.single()
        val alternate = DesktopProxyImportPlanner.planText(differentLiteralForSameConnection).servers.single()
        val subscription = "https://provider.example.com/subscription"
        assertEquals(parsed.connectionFingerprint(), alternate.connectionFingerprint())

        val plan = DesktopProxyImportPlanner.planText(
            text = "$server\n$server\n$differentLiteralForSameConnection\n$subscription\n$subscription",
            existing = DesktopProxyImportExisting(
                serverFingerprints = setOf(parsed.connectionFingerprint()),
                subscriptionUrls = setOf(subscription),
                serverDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated,
                subscriptionDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated,
            ),
        )

        assertEquals(listOf("One", "Alternate"), plan.servers.map { server -> server.getInfo().remarks })
        assertEquals(listOf(subscription), plan.subscriptions)
        assertTrue(plan.diagnostics.none { it.code == DesktopProxyImportDiagnosticCode.DuplicateServers })
        assertTrue(plan.diagnostics.any { it.code == DesktopProxyImportDiagnosticCode.DuplicateSubscriptions })
    }
}
