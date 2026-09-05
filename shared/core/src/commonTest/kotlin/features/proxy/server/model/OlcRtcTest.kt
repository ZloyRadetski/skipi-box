// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OlcRtcTest {

    @Test
    fun parseOlcRtcUrlAndGenerateOutbound() {
        // URI format: olcrtc://<Provider>?<Transport>[<payload>]@<RoomID>#<EncryptionKey>$<Remarks>
        val url = "olcrtc://jitsi?datachannel[mode=fast]@my-secret-room#0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\$MeetingProxy"
        val server = assertIs<OlcRtc>(ProxyServer.parse(url))

        assertEquals("MeetingProxy", server.remarks)
        assertEquals("jitsi", server.provider)
        assertEquals("datachannel", server.transport)
        assertEquals("my-secret-room", server.roomUrl)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", server.encryptionKey)
        assertEquals("mode=fast", server.payload)

        // Test outbound generation (SOCKS5 to localhost:10808)
        val outbound = server.toXrayOutbound("olcrtc-tag")
        assertEquals("olcrtc-tag", outbound.tag)
        assertEquals(ProxyServerConstants.PROTOCOL_SOCKS, outbound.protocol)

        // Test YAML config generation
        val yaml = server.toOlcRtcYamlConfig(10808)
        assertTrue(yaml.contains("mode: cnc"))
        assertTrue(yaml.contains("provider: jitsi"))
        assertTrue(yaml.contains("transport: datachannel[mode=fast]"))
        assertTrue(yaml.contains("room: my-secret-room"))
        assertTrue(yaml.contains("socks5_listen: 127.0.0.1:10808"))

        // Test YAML config generation with credentials
        val authYaml = server.toOlcRtcYamlConfig(12345, socksUser = "user1", socksPass = "pass1")
        assertTrue(authYaml.contains("socks5_listen: 127.0.0.1:12345"))
        assertTrue(authYaml.contains("socks5_user: user1"))
        assertTrue(authYaml.contains("socks5_pass: pass1"))

        // Test Xray outbound generation with port and credentials
        val authOutbound = server.toXrayOutboundWithPortAndAuth("custom-olc", 12345, "user1", "pass1")
        assertEquals("custom-olc", authOutbound.tag)
        val json = authOutbound.toJsonObject().toString()
        assertTrue(json.contains("12345"))
        assertTrue(json.contains("user1"))
        assertTrue(json.contains("pass1"))
    }


    @Test
    fun persistenceRoundTrip() {
        val original = OlcRtc(
            remarks = "CustomRoom",
            provider = "telemost",
            transport = "vp8channel",
            roomUrl = "room123",
            encryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            payload = "fps=30",
            localSocksPort = "10809",
        )

        val restored = assertIs<OlcRtc>(original.encodePersistedProxyServer().decodePersistedProxyServer())
        assertEquals(original.remarks, restored.remarks)
        assertEquals(original.provider, restored.provider)
        assertEquals(original.transport, restored.transport)
        assertEquals(original.roomUrl, restored.roomUrl)
        assertEquals(original.encryptionKey, restored.encryptionKey)
        assertEquals(original.payload, restored.payload)
        assertEquals(original.localSocksPort, restored.localSocksPort)
    }

    @Test
    fun validationRejectsInvalidHexKey() {
        val invalidKey = OlcRtc(
            remarks = "InvalidKey",
            provider = "jitsi",
            transport = "datachannel",
            roomUrl = "room",
            encryptionKey = "short_non_hex_key",
        )

        val issues = invalidKey.validateFull()
        assertTrue(issues.any { it.error == ProxyServerValidationError.OlcRtcEncryptionKeyInvalid })
    }

    @Test
    fun validationRejectsInvalidProvider() {
        val invalidProvider = OlcRtc(
            remarks = "BadProvider",
            provider = "unsupported_provider",
            transport = "datachannel",
            roomUrl = "room",
            encryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )

        val issues = invalidProvider.validateFull()
        assertTrue(issues.any { it.error == ProxyServerValidationError.OlcRtcProviderInvalid })
    }

    @Test
    fun testSignalingEndpointResolution() {
        // HTTPS full URL
        val httpsServer = OlcRtc(
            provider = "telemost",
            roomUrl = "https://telemost.yandex.ru/j/1234567890",
        )
        val httpsEndpoint = httpsServer.signalingEndpoint()
        assertNotNull(httpsEndpoint)
        assertEquals("telemost.yandex.ru", httpsEndpoint.first)
        assertEquals(443, httpsEndpoint.second)

        // Custom host:port in roomUrl
        val customPortServer = OlcRtc(
            provider = "custom",
            roomUrl = "my.signaling.host:8443/room",
        )
        val customPortEndpoint = customPortServer.signalingEndpoint()
        assertNotNull(customPortEndpoint)
        assertEquals("my.signaling.host", customPortEndpoint.first)
        assertEquals(8443, customPortEndpoint.second)

        // Provider fallback for Jitsi
        val jitsiServer = OlcRtc(
            provider = "jitsi",
            roomUrl = "my-secret-room",
        )
        val jitsiEndpoint = jitsiServer.signalingEndpoint()
        assertNotNull(jitsiEndpoint)
        assertEquals("meet.jit.si", jitsiEndpoint.first)
        assertEquals(443, jitsiEndpoint.second)

        // Provider fallback for Telemost
        val telemostServer = OlcRtc(
            provider = "telemost",
            roomUrl = "1234567890",
        )
        val telemostEndpoint = telemostServer.signalingEndpoint()
        assertNotNull(telemostEndpoint)
        assertEquals("telemost.yandex.ru", telemostEndpoint.first)
        assertEquals(443, telemostEndpoint.second)
    }

    @Test
    fun testMatchesEndpoint() {
        val server1 = OlcRtc(provider = "jitsi", roomUrl = "room-1", encryptionKey = "key1")
        val server2 = OlcRtc(provider = "jitsi", roomUrl = "room-1", encryptionKey = "key2")
        val server3 = OlcRtc(provider = "jitsi", roomUrl = "room-2", encryptionKey = "key1")

        assertTrue(server1.matchesEndpoint(server2))
        assertTrue(!server1.matchesEndpoint(server3))
    }
}

