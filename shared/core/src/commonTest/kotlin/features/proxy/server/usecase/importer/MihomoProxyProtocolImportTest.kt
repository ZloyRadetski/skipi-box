// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.HTTP
import features.proxy.server.model.Socks
import features.proxy.server.model.VMess
import features.proxy.server.model.VLESS
import features.proxy.server.model.Wireguard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MihomoProxyProtocolImportTest {
    @Test
    fun convertsBasicProtocolsWithOneSharedModel() {
        val http = mapOf(
            "name" to "HTTP node",
            "server" to "http.example",
            "port" to 8080,
            "username" to "alice",
            "password" to "secret",
        ).toMihomoHttpProxyServer()
        val vmess = mapOf(
            "name" to "VMess node",
            "server" to "vmess.example",
            "port" to "443",
            "uuid" to "c2f3f881-76bc-4080-9f25-94b8c9ccaa62",
            "network" to "grpc",
            "grpc-opts" to mapOf("service-name" to "service"),
        ).toMihomoVMessProxyServer()

        assertIs<HTTP>(http)
        assertEquals("alice", http.user)
        assertIs<VMess>(vmess)
        assertEquals("grpc", vmess.parms.type)
        assertEquals("service", vmess.parms.serviceName)
    }

    @Test
    fun convertsWireguardPeersAndRejectsUnsupportedTlsForHttp() {
        val wireguard = mapOf(
            "name" to "WG node",
            "private-key" to "private",
            "ip" to "10.0.0.2",
            "peers" to listOf(
                mapOf("server" to "wg.example", "port" to 51820, "public-key" to "public"),
            ),
        ).toMihomoWireguardProxyServer()

        assertIs<Wireguard>(wireguard)
        assertEquals("10.0.0.2/32", wireguard.address)
        assertEquals("wg.example", wireguard.server)
        assertFailsWith<UnsupportedMihomoProxyException> {
            mapOf(
                "name" to "TLS HTTP",
                "server" to "http.example",
                "port" to 443,
                "tls" to true,
            ).toMihomoHttpProxyServer()
        }
    }

    @Test
    fun convertsVlessXhttpFieldsInTheSharedImporter() {
        val vless = mapOf(
            "name" to "XHTTP node",
            "server" to "vless.example",
            "port" to 443,
            "uuid" to "c2f3f881-76bc-4080-9f25-94b8c9ccaa62",
            "network" to "xhttp",
            "xhttp-opts" to mapOf(
                "path" to "/split",
                "host" to "cdn.example",
                "mode" to "auto",
            ),
        ).toMihomoVlessProxyServer()

        assertIs<VLESS>(vless)
        assertEquals("xhttp", vless.parms.type)
        assertEquals("/split", vless.parms.path)
        assertEquals("cdn.example", vless.parms.host)
        assertEquals("auto", vless.parms.mode)
    }

    @Test
    fun dispatchesOnlySupportedTypesThroughTheSharedConverter() {
        assertEquals(true, "vless".isSupportedMihomoProxyType())
        assertEquals(false, "tuic".isSupportedMihomoProxyType())
        val server = mapOf(
            "type" to "socks5",
            "name" to "SOCKS node",
            "server" to "socks.example",
            "port" to 1080,
        ).toMihomoProxyServer()
        val socks = assertIs<Socks>(server)
        assertEquals("socks.example", socks.server)
    }
}
