// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProxyCredentialsTest {
    private val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    @Test
    fun credentialIsSixteenAlphanumericCharactersAfterThePrefix() {
        val credential = generateRandomProxyCredential()

        assertTrue(credential.startsWith("skipi_"))
        val suffix = credential.removePrefix("skipi_")
        assertEquals(16, suffix.length)
        assertTrue(suffix.all { it in alphabet })
    }

    @Test
    fun credentialsDiffer() {
        assertNotEquals(generateRandomProxyCredential(), generateRandomProxyCredential())
    }

    @Test
    fun everyCharacterComesFromTheInjectedIndexSource() {
        val credential = generateRandomProxyCredential(prefix = "p_", nextIndex = { bound -> bound - 1 })

        assertEquals("p_" + "9".repeat(16), credential)
    }
}
