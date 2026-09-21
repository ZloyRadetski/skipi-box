// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MihomoV2RayParametersTest {
    @Test
    fun normalizesYamlScalarsAndBuildsWebsocketEarlyDataParameters() {
        val config: MihomoYamlMap = mapOf(
            "network" to "ws",
            "tls" to "true",
            "servername" to "node.example",
            "alpn" to listOf("h2", "http/1.1"),
            "ws-opts" to mapOf(
                "path" to "/socket",
                "max-early-data" to 2_048,
                "headers" to mapOf("Host" to "cdn.example", "X-Trace" to "enabled"),
            ),
        )

        val parameters = config.toMihomoV2RayParameters(defaultSecurity = "none")

        assertEquals("websocket", parameters.type)
        assertEquals("tls", parameters.security)
        assertEquals("/socket?ed=2048", parameters.path)
        assertEquals("cdn.example", parameters.host)
        assertEquals("h2,http/1.1", parameters.alpn)
        assertEquals("node.example", parameters.sni)
        assertEquals("{\"X-Trace\":\"enabled\"}", parameters.headers)
    }

    @Test
    fun rejectsUnsupportedTransportSecurityOptionsBeforePlatformCodeRuns() {
        assertFailsWith<UnsupportedMihomoProxyException> {
            mapOf("network" to "h2").toMihomoV2RayParameters(defaultSecurity = "none")
        }
        assertFailsWith<UnsupportedMihomoProxyException> {
            mapOf("skip-cert-verify" to true).toMihomoV2RayParameters(defaultSecurity = "none")
        }
    }

    @Test
    fun scalarAlpnIsRecognizedAsTlsLikeDesktopYamlImport() {
        val parameters = mapOf(
            "alpn" to "h2",
        ).toMihomoV2RayParameters(defaultSecurity = "none")

        assertEquals("tls", parameters.security)
        assertEquals("h2", parameters.alpn)
    }
}
