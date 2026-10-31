// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopExternalLinkPolicyTest {
    @Test
    fun allows_https_links_and_one_plain_mailto_recipient() {
        assertEquals(
            "https://support.example.com/help",
            requireSafeDesktopExternalUri("https://support.example.com/help").toString(),
        )
        assertEquals(
            "mailto:help@example.com",
            requireSafeDesktopExternalUri("mailto:help@example.com").toString(),
        )
    }

    @Test
    fun rejects_untrusted_protocol_handlers_and_mailto_parameters() {
        listOf(
            "http://support.example.com/help",
            "file:///C:/Windows/System32/calc.exe",
            "ms-settings:privacy",
            "mailto:help@example.com?subject=hello",
            "mailto:not-an-email",
            "https://user:password@support.example.com/help",
        ).forEach { value ->
            assertFailsWith<RuntimeException> {
                requireSafeDesktopExternalUri(value)
            }
        }
    }
}
