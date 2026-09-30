// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProxyHomeAndroidExternalUriTest {
    @Test
    fun allowsHttpsAndSingleAddressMailtoOnly() {
        assertEquals(
            "https://support.example.com/help",
            requireSafeAndroidExternalUri("https://support.example.com/help").toString(),
        )
        assertEquals(
            "mailto:help@example.com",
            requireSafeAndroidExternalUri("mailto:help@example.com").toString(),
        )
    }

    @Test
    fun rejectsUnsafeSchemesCredentialsAndMailtoParameters() {
        listOf(
            "http://support.example.com/help",
            "file:///etc/passwd",
            "content://provider/item",
            "mailto:help@example.com?subject=hello",
            "mailto:not-an-email",
            "https://user:password@support.example.com/help",
        ).forEach { value ->
            assertThrows(RuntimeException::class.java) {
                requireSafeAndroidExternalUri(value)
            }
        }
    }
}
