// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.importer

import features.proxy.server.model.HTTP
import features.proxy.server.model.Socks
import features.proxy.server.model.Custom
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importProxyServersFromText
import features.proxy.server.usecase.importer.parseProxyServersFromMihomoYamlConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProxyServerMihomoYamlImportTest {
    private val context = ProxyServerImportContext(
        source = ProxyServerImportSource.Clipboard,
    )

    @Test
    fun importsScalarAliasesAndBlockScalarCredentialsForSocksAndHttp() = runTest {
        val result = parseProxyServersFromMihomoYamlConfig(anchoredYamlFixture, context)

        assertEquals(2, result.urlCount)
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
    fun leadingBomIsIgnoredBeforeParsingValidYaml() = runTest {
        val result = parseProxyServersFromMihomoYamlConfig("\uFEFF$anchoredYamlFixture", context)

        assertEquals(2, result.urlCount)
        assertEquals(2, result.servers.size)
        assertIs<Socks>(result.servers[0])
        assertIs<HTTP>(result.servers[1])
    }

    @Test
    fun emptyOrMalformedLeadingBomYamlReturnsNoServers() = runTest {
        listOf("\uFEFF", "\uFEFFproxies: [").forEach { payload ->
            val result = parseProxyServersFromMihomoYamlConfig(payload, context)

            assertEquals(0, result.urlCount)
            assertTrue(result.servers.isEmpty())
        }
    }

    @Test
    fun importsJsonArrayAsCustomConfigsBeforeYamlClassification() = runTest {
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

        val result = importProxyServersFromText(jsonPayload, ProxyServerImportSource.Clipboard)

        assertEquals(2, result.urlCount)
        assertEquals(2, result.servers.size)
        val amsterdam = assertIs<Custom>(result.servers[0])
        assertEquals("Amsterdam Node", amsterdam.remarks)
        val helsinki = assertIs<Custom>(result.servers[1])
        assertEquals("Helsinki Node", helsinki.remarks)
    }
}

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
