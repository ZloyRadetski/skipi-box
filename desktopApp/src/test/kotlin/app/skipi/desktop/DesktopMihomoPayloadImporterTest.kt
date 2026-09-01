// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.Hysteria2
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopMihomoPayloadImporterTest {
    @Test
    fun importsTypicalMihomoProxyListWithoutYamlRuntimeDependency() {
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
    fun deDuplicatesEquivalentNodesAcrossTheRootAndProvider() {
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

        assertEquals(2, result.proxyEntryCount)
        assertEquals(1, result.servers.size)
        assertEquals("Root SS", assertIs<Shadowsocks>(result.servers.single()).remarks)
    }
}
