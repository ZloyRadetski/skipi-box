// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmneziaWgTest {

    @Test
    fun parseAwgUrlAndGenerateOutbound() {
        val url = "awg://aGVsbG8=@192.168.1.100:51820?publickey=d29ybGQ=&jc=5&jmin=30&jmax=80&s1=20&s2=40&h1=100&h2=200&h3=300&h4=400#MyAWG"
        val server = assertIs<AmneziaWg>(ProxyServer.parse(url))

        assertEquals("MyAWG", server.remarks)
        assertEquals("192.168.1.100", server.server)
        assertEquals("51820", server.port)
        assertEquals("aGVsbG8=", server.secretKey)
        assertEquals("d29ybGQ=", server.publicKey)
        assertEquals("5", server.jc)
        assertEquals("30", server.jmin)
        assertEquals("80", server.jmax)
        assertEquals("20", server.s1)
        assertEquals("40", server.s2)
        assertEquals("100", server.h1)
        assertEquals("200", server.h2)
        assertEquals("300", server.h3)
        assertEquals("400", server.h4)

        val outbound = server.toXrayOutbound("test-tag")
        assertEquals("test-tag", outbound.tag)
        assertEquals(ProxyServerConstants.PROTOCOL_WIREGUARD, outbound.protocol)

        val settings = outbound.settings
        assertEquals("aGVsbG8=", settings["secretKey"]?.toString()?.removeSurrounding("\""))
        assertEquals(5, settings["jc"]?.toString()?.toIntOrNull())
        assertEquals(30, settings["jmin"]?.toString()?.toIntOrNull())
        assertEquals(80, settings["jmax"]?.toString()?.toIntOrNull())
        assertEquals(20, settings["s1"]?.toString()?.toIntOrNull())
        assertEquals(40, settings["s2"]?.toString()?.toIntOrNull())
        assertEquals(100L, settings["h1"]?.toString()?.toLongOrNull())
    }

    @Test
    fun persistenceRoundTrip() {
        val original = AmneziaWg(
            remarks = "TestAWG",
            server = "1.2.3.4",
            port = "51820",
            secretKey = "a2V5",
            publicKey = "cHVi",
            jc = "7",
            jmin = "50",
            jmax = "90",
            s1 = "12",
            s2 = "24",
            h1 = "999",
        )

        val restored = assertIs<AmneziaWg>(original.encodePersistedProxyServer().decodePersistedProxyServer())
        assertEquals(original.remarks, restored.remarks)
        assertEquals(original.server, restored.server)
        assertEquals(original.port, restored.port)
        assertEquals(original.secretKey, restored.secretKey)
        assertEquals(original.publicKey, restored.publicKey)
        assertEquals(original.jc, restored.jc)
        assertEquals(original.jmin, restored.jmin)
        assertEquals(original.jmax, restored.jmax)
        assertEquals(original.s1, restored.s1)
        assertEquals(original.s2, restored.s2)
        assertEquals(original.h1, restored.h1)
    }

    @Test
    fun validationRejectsInvalidJminJmax() {
        val invalid = AmneziaWg(
            remarks = "Invalid",
            server = "1.2.3.4",
            port = "51820",
            secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
            publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
            jc = "4",
            jmin = "100",
            jmax = "50", // jmin > jmax
        )

        val issues = invalid.validateFull()
        assertTrue(issues.any { it.error == ProxyServerValidationError.AmneziaWgJminMaxInvalid })
    }
}
