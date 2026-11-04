// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalProxyAuthTest {
    private val stored = LocalProxyCredentials("stored-user", "stored-pass")
    private val json = LocalProxyCredentials("json-user", "json-pass")

    @Test
    fun offNeverAuthenticates() {
        assertEquals(LocalProxyCredentials.None, LocalProxyAuth.resolve(LocalProxyAuthMode.Off, stored, json))
    }

    @Test
    fun manualUsesTheStoredLoginAndIgnoresTheJson() {
        assertEquals(stored, LocalProxyAuth.resolve(LocalProxyAuthMode.Manual, stored, json))
    }

    @Test
    fun manualWithoutAUsernameMeansNoAuthentication() {
        val blank = LocalProxyCredentials("  ", "secret")
        assertEquals(LocalProxyCredentials.None, LocalProxyAuth.resolve(LocalProxyAuthMode.Manual, blank, null))
    }

    @Test
    fun storedUsernamesAreTrimmedLikeTheExistingListener() {
        val padded = LocalProxyCredentials(" proxy-user ", "secret")
        assertEquals(
            LocalProxyCredentials("proxy-user", "secret"),
            LocalProxyAuth.resolve(LocalProxyAuthMode.Manual, padded, null),
        )
    }

    @Test
    fun fromJsonUsesOnlyTheLoginTheJsonCarries() {
        assertEquals(json, LocalProxyAuth.resolve(LocalProxyAuthMode.FromJson, stored, json))
        assertEquals(LocalProxyCredentials.None, LocalProxyAuth.resolve(LocalProxyAuthMode.FromJson, stored, null))
    }

    @Test
    fun autoPrefersTheJsonLoginAndFallsBackToTheGeneratedOne() {
        assertEquals(json, LocalProxyAuth.resolve(LocalProxyAuthMode.Auto, stored, json))
        assertEquals(stored, LocalProxyAuth.resolve(LocalProxyAuthMode.Auto, stored, null))
    }

    @Test
    fun readsSocksUsersAndHttpAccountsFromTheInbounds() {
        val auth = LocalProxyAuth.readJsonAuth(
            """
            {"inbounds": [
              {"protocol": "socks", "port": 1080, "settings": {"auth": "password", "users": [{"user": "su", "pass": "sp"}]}},
              {"protocol": "http", "port": 8080, "settings": {"accounts": [{"user": "hu", "pass": "hp"}]}}
            ]}
            """.trimIndent(),
        )

        assertEquals(LocalProxyCredentials("su", "sp"), auth.socks)
        assertEquals(LocalProxyCredentials("hu", "hp"), auth.http)
    }

    @Test
    fun anInboundWithoutPasswordAuthCarriesNoLogin() {
        val auth = LocalProxyAuth.readJsonAuth(
            """{"inbounds": [{"protocol": "socks", "settings": {"auth": "noauth", "users": [{"user": "x", "pass": "y"}]}}]}""",
        )

        assertNull(auth.socks)
        assertNull(auth.http)
    }

    @Test
    fun theFirstUsableInboundOfEachProtocolWins() {
        val auth = LocalProxyAuth.readJsonAuth(
            """
            {"inbounds": [
              {"protocol": "socks", "settings": {"auth": "noauth"}},
              {"protocol": "socks", "settings": {"auth": "password", "users": [{"user": "first", "pass": "1"}, {"user": "second", "pass": "2"}]}}
            ]}
            """.trimIndent(),
        )

        assertEquals(LocalProxyCredentials("first", "1"), auth.socks)
    }

    @Test
    fun brokenOrEmptyJsonCarriesNoLogin() {
        assertEquals(LocalProxyJsonAuth(), LocalProxyAuth.readJsonAuth("not json"))
        assertEquals(LocalProxyJsonAuth(), LocalProxyAuth.readJsonAuth("{}"))
        assertEquals(LocalProxyJsonAuth(), LocalProxyAuth.readJsonAuth(""))
    }
}
