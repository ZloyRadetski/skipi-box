// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SafeExternalUriTest {
    private val localized = ExternalLinkMessages(
        httpsHostRequired = "h1",
        httpsNoCredentials = "h2",
        mailtoSingleAddress = "m1",
        mailtoInvalidAddress = "m2",
        unsupportedScheme = "u",
    )

    private fun messageOf(value: String): String? =
        runCatching { requireSafeExternalUri(value, localized) }.exceptionOrNull()?.message

    @Test
    fun acceptsHttpsAndOneMailtoRecipient() {
        assertEquals("https://support.example.com/help", requireSafeExternalUri("https://support.example.com/help").toString())
        assertEquals("mailto:help@example.com", requireSafeExternalUri("mailto:help@example.com").toString())
    }

    @Test
    fun refusalsUseTheCallersMessages() {
        assertEquals("h1", messageOf("https:///path"))
        assertEquals("h2", messageOf("https://user:pass@example.com/"))
        assertEquals("m1", messageOf("mailto:a@example.com?subject=x"))
        assertEquals("m2", messageOf("mailto:not-an-address"))
        assertEquals("u", messageOf("http://example.com"))
        assertEquals("u", messageOf("file:///etc/passwd"))
    }

    @Test
    fun defaultMessagesAreEnglish() {
        assertFailsWith<IllegalStateException> { requireSafeExternalUri("javascript:alert(1)") }
    }
}
