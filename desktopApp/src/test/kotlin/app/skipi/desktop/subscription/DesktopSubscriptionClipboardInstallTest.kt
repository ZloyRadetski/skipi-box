// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.DefaultSubscriptionUserAgent

import features.subscription.SubscriptionInstallSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSubscriptionClipboardInstallTest {
    @Test
    fun existing_clipboard_route_accepts_encoded_shared_skipi_subscription_links() {
        val link = "skipi://add/https%3A%2F%2Fexample.test%2Fsub%3Ftoken%3Da%2Bb%23remote"

        val install = checkNotNull(link.toDesktopSubscriptionInstallClipboardUriOrNull())

        assertEquals(
            DesktopSubscriptionInstallUri(
                name = "SKIPI subscription",
                url = "https://example.test/sub?token=a+b#remote",
                userAgent = DefaultSubscriptionUserAgent,
                source = SubscriptionInstallSource.RawHttp,
            ),
            install,
        )
        // The Add dialog still uses parseOrNull and therefore remains HTTP(S)/client-URI only.
        assertNull(DesktopSubscriptionInstallUri.parseOrNull(link))

        val importedLink = "skipi://import/https%3A%2F%2Fexample.test%2Fother"
        assertEquals(
            install.copy(url = "https://example.test/other"),
            importedLink.toDesktopSubscriptionInstallClipboardUriOrNull(),
        )
    }

    @Test
    fun clipboard_route_keeps_existing_install_parsing_and_ignores_malformed_or_non_subscription_links() {
        val existing = checkNotNull(
            DesktopSubscriptionInstallUri.parseOrNull(
                "v2rayng://install-config?url=https%3A%2F%2Fexample.test%2Fsub&name=Client%20name",
            ),
        )

        val clientLink = "v2rayng://install-config?url=https%3A%2F%2Fexample.test%2Fsub&name=Client%20name"
        assertEquals(existing, clientLink.toDesktopSubscriptionInstallClipboardUriOrNull())
        assertNull("skipi://add/not-a-url".toDesktopSubscriptionInstallClipboardUriOrNull())
        assertNull("skipi://add/".toDesktopSubscriptionInstallClipboardUriOrNull())
        assertNull("skipi://connect".toDesktopSubscriptionInstallClipboardUriOrNull())
        assertNull(
            "skipi://add/vless%3A%2F%2Fuuid%40node.example%3A443"
                .toDesktopSubscriptionInstallClipboardUriOrNull(),
        )
    }
}
