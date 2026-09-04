// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
}
