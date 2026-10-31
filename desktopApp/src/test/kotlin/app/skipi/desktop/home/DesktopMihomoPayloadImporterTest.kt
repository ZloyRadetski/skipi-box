// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.HTTP
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopMihomoPayloadImporterTest {
    @Test
    fun importsScalarAliasesAndBlockScalarCredentialsForSocksAndHttp() {
        val result = DesktopMihomoPayloadImporter.import(anchoredYamlFixture)

        assertTrue(result.recognizedYaml)
        assertEquals(2, result.proxyEntryCount)
        assertEquals(0, result.rejectedProxyCount)
        assertEquals(2, result.servers.size)

        val socks = assertIs<Socks>(result.servers[0])
        assertEquals("Socks shared scalar", socks.remarks)
        assertEquals("proxy.example.test", socks.server)
        assertEquals("1080", socks.port)
        assertEquals("shared-user", socks.user)
        assertEquals("first secret line\nsecond secret line", socks.password)

        val http = assertIs<HTTP>(result.servers[1])
        assertEquals("HTTP alias node", http.remarks)
        assertEquals("proxy.example.test", http.server)
        assertEquals("1080", http.port)
        assertEquals("shared-user", http.user)
        assertEquals("first secret line\nsecond secret line", http.password)
    }

    @Test
    fun importsTypicalMihomoProxyListWithSharedYamlLoader() {
        val result = DesktopMihomoPayloadImporter.import(
            """
            proxies:
              - name: "NL VLESS"
                type: vless
                server: edge.example.com
                port: 443
                uuid: 8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6
                network: ws
                tls: true
                servername: cdn.example.com
                ws-opts:
                  path: /websocket
                  headers:
                    Host: cdn.example.com
              - { name: "SS node", type: ss, server: ss.example.com, port: 443, cipher: aes-256-gcm, password: secret }
            """.trimIndent(),
        )

        assertTrue(result.recognizedYaml)
        assertEquals(2, result.proxyEntryCount)
        assertEquals(0, result.rejectedProxyCount)
        assertEquals(2, result.servers.size)

        val vless = assertIs<VLESS>(result.servers[0])
        assertEquals("NL VLESS", vless.remarks)
        assertEquals("edge.example.com", vless.server)
        assertEquals("websocket", vless.parms.type)
        assertEquals("tls", vless.parms.security)
        assertEquals("/websocket", vless.parms.path)
        assertEquals("cdn.example.com", vless.parms.host)

        val shadowsocks = assertIs<Shadowsocks>(result.servers[1])
        assertEquals("aes-256-gcm", shadowsocks.method)
        assertEquals("secret", shadowsocks.password)
    }

    @Test
    fun usesSharedV2RayTransportMappingForDesktopYaml() {
        val result = DesktopMihomoPayloadImporter.import(
            """
                proxies:
                  - name: Shared upgrade node
                    type: vless
                    server: edge.example.com
                    port: 443
                    uuid: 8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6
                    network: ws
                    ws-headers:
                      X-Trace: desktop
                    ws-opts:
                      path: /upgrade
                      max-early-data: 1024
                      v2ray-http-upgrade: true
                      headers:
                        Host: cdn.example.com
            """.trimIndent(),
        )

        val server = assertIs<VLESS>(result.servers.single())
        assertEquals("httpupgrade", server.parms.type)
        assertEquals("/upgrade?ed=1024", server.parms.path)
        assertEquals("cdn.example.com", server.parms.host)
        assertEquals("{\"X-Trace\":\"desktop\"}", server.parms.headers)
    }

    @Test
    fun importsInlineAndProvidedHttpProviderPayloadsAndReportsMissingOnes() {
        val remoteUrl = "https://provider.example.com/nodes.yaml"
        val missingUrl = "https://provider.example.com/missing.yaml"
        val result = DesktopMihomoPayloadImporter.import(
            text = """
                proxy-providers:
                  inline:
                    type: inline
                    payload:
                      - name: Inline Trojan
                        type: trojan
                        server: trojan.example.com
                        port: 443
                        password: secret
                        sni: trojan.example.com
                  remote:
                    type: http
                    url: $remoteUrl
                  missing:
                    type: http
                    url: $missingUrl
            """.trimIndent(),
            providerPayloads = mapOf(
                remoteUrl to """
                    proxies:
                      - name: Provider HY2
                        type: hysteria2
                        server: hy2.example.com
                        port: 443
                        password: provider-secret
                        sni: hy2.example.com
                """.trimIndent(),
            ),
        )

        assertTrue(result.recognizedYaml)
        assertEquals(2, result.servers.size)
        assertIs<Trojan>(result.servers[0])
        assertIs<Hysteria2>(result.servers[1])
        assertEquals(listOf(DesktopMihomoProviderRequest("missing", missingUrl)), result.pendingProviders)
    }

    @Test
    fun nonemptyHttpProviderBodyReplacesInlineFallbackAndKeepsRootOrder() {
        val providerUrl = "https://provider.example.com/remote.yaml"
        val result = DesktopMihomoPayloadImporter.import(
            text = """
                proxies:
                  - name: Root node
                    type: socks
                    server: root.example.com
                    port: 1080
                proxy-providers:
                  remote:
                    type: http
                    url: $providerUrl
                    payload:
                      - name: Inline fallback
                        type: socks
                        server: inline.example.com
                        port: 1080
            """.trimIndent(),
            providerPayloads = mapOf(
                providerUrl to """
                    proxies:
                      - name: Remote node
                        type: socks
                        server: remote.example.com
                        port: 1080
                """.trimIndent(),
            ),
        )

        assertTrue(result.recognizedYaml)
        assertEquals(4, result.proxyEntryCount)
        assertEquals(
            listOf("Root node", "Remote node"),
            result.servers.map { it.getInfo().remarks },
        )
        assertTrue(result.pendingProviders.isEmpty())
    }

    @Test
    fun emptyProviderBodyFallsBackToItsInlinePayloadAndCountsTheFallback() {
        val providerUrl = "https://provider.example.com/empty.yaml"
        val result = DesktopMihomoPayloadImporter.import(
            text = httpProviderWithInlineSocks(providerUrl, "Inline after empty", "inline-empty.example.com"),
            providerPayloads = mapOf(providerUrl to "proxies: []"),
        )

        assertEquals(2, result.proxyEntryCount)
        assertEquals(listOf("Inline after empty"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun malformedProviderBodyFallsBackToItsInlinePayloadAndCountsTheFallback() {
        val providerUrl = "https://provider.example.com/malformed.yaml"
        val result = DesktopMihomoPayloadImporter.import(
            text = httpProviderWithInlineSocks(providerUrl, "Inline after failure", "inline-failed.example.com"),
            providerPayloads = mapOf(providerUrl to "proxies: ["),
        )

        assertEquals(2, result.proxyEntryCount)
        assertEquals(listOf("Inline after failure"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun literalAncestorProviderUrlUsesTheNestedInlineFallback() {
        val providerUrl = "https://provider.example.com/ancestor.yaml"
        val result = DesktopMihomoPayloadImporter.import(
            text = httpProviderWithInlineSocks(providerUrl, "Outer inline fallback", "outer-inline.example.com"),
            providerPayloads = mapOf(
                providerUrl to httpProviderWithInlineSocks(
                    providerUrl,
                    "Cycle inline fallback",
                    "cycle-inline.example.com",
                ),
            ),
        )

        assertEquals(4, result.proxyEntryCount)
        assertEquals(listOf("Cycle inline fallback"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun mihomoInlineStringPayloadIsNotRecursivelyTreatedAsAProxyUrl() {
        val result = DesktopMihomoPayloadImporter.import(
            text = """
                proxies:
                  - name: Root node
                    type: socks
                    server: root.example.com
                    port: 1080
                proxy-providers:
                  inline:
                    type: inline
                    payload: 'vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@inline.example.com:443#Inline'
            """.trimIndent(),
        )

        assertTrue(result.recognizedYaml)
        assertEquals(2, result.proxyEntryCount)
        assertEquals(listOf("Root node"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun importsPortableAmneziaWgYamlEvenWhenDesktopNativeRuntimeCannotRunIt() {
        val result = DesktopMihomoPayloadImporter.import(
            text = """
                proxies:
                  - name: Portable AWG node
                    type: awg
                    server: 192.0.2.10
                    port: 51820
                    private-key: aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
                    public-key: YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
                    ip: 10.0.0.2
            """.trimIndent(),
        )

        assertTrue(result.recognizedYaml)
        assertEquals(1, result.proxyEntryCount)
        assertEquals(0, result.rejectedProxyCount)
        val awg = assertIs<AmneziaWg>(result.servers.single())
        assertEquals("Portable AWG node", awg.getInfo().remarks)
        assertEquals("192.0.2.10", awg.server)
    }

    @Test
    fun preservesEquivalentNodesAcrossTheRootAndProvider() {
        val providerUrl = "https://provider.example.com/duplicate.yaml"
        val result = DesktopMihomoPayloadImporter.import(
            text = """
                proxies:
                  - name: Root SS
                    type: ss
                    server: duplicate.example.com
                    port: 443
                    cipher: aes-256-gcm
                    password: secret
                proxy-providers:
                  remote:
                    type: http
                    url: $providerUrl
            """.trimIndent(),
            providerPayloads = mapOf(
                providerUrl to """
                    payload:
                      - name: Provider SS with another label
                        type: ss
                        server: duplicate.example.com
                        port: 443
                        cipher: aes-256-gcm
                        password: secret
                """.trimIndent(),
            ),
        )

        assertEquals(4, result.proxyEntryCount)
        assertEquals(2, result.servers.size)
        assertEquals(
            listOf("Root SS", "Provider SS with another label"),
            result.servers.map { assertIs<Shadowsocks>(it).remarks },
        )
    }

    @Test
    fun importsJsonArrayOfXrayConfigs() {
        val jsonPayload = """
            [
              {
                "tag": "Amsterdam Node",
                "inbounds": [
                  { "tag": "socks-in", "listen": "127.0.0.1", "port": 10808, "protocol": "socks" }
                ],
                "outbounds": [
                  {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                      "vnext": [{ "address": "ams.example.com", "port": 443, "users": [{ "id": "uuid-123" }] }]
                    }
                  },
                  { "tag": "direct", "protocol": "freedom" }
                ]
              },
              {
                "remarks": "Helsinki Node",
                "outbounds": [
                  {
                    "tag": "proxy",
                    "protocol": "hysteria2",
                    "settings": {
                      "servers": [{ "address": "hel.example.com", "port": 443 }]
                    }
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = DesktopMihomoPayloadImporter.import(jsonPayload)

        assertEquals(false, result.recognizedYaml)
        assertEquals(2, result.proxyEntryCount)
        assertEquals(2, result.servers.size)
        assertEquals(0, result.rejectedProxyCount)

        val server1 = kotlin.test.assertIs<features.proxy.server.model.Custom>(result.servers[0])
        assertEquals("Amsterdam Node", server1.remarks)

        val server2 = kotlin.test.assertIs<features.proxy.server.model.Custom>(result.servers[1])
        assertEquals("Helsinki Node", server2.remarks)
    }
}

private fun httpProviderWithInlineSocks(providerUrl: String, name: String, server: String): String = """
    proxy-providers:
      remote:
        type: http
        url: $providerUrl
        payload:
          - name: $name
            type: socks
            server: $server
            port: 1080
""".trimIndent()

private val anchoredYamlFixture = """
    proxies:
      - name: Socks shared scalar
        type: socks
        server: &sharedServer proxy.example.test
        port: &sharedPort "1080"
        username: &sharedUser shared-user
        password: &sharedPassword |
          first secret line
          second secret line
      - name: HTTP alias node
        type: http
        server: *sharedServer
        port: *sharedPort
        username: *sharedUser
        password: *sharedPassword
""".trimIndent()
