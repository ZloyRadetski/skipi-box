// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSubscriptionInstallUriTest {
    @Test
    fun parses_raw_http_and_https_subscriptions_with_fragment_name() {
        val https = DesktopSubscriptionInstallUri.parseOrNull(
            "https://example.com/subscription#My%20VPN%2BOne",
        )
        val http = "http://example.com/subscription".toDesktopSubscriptionInstallUriOrNull()

        assertEquals(DesktopSubscriptionInstallSource.RawHttp, https?.source)
        assertEquals("My VPN+One", https?.name)
        assertEquals("https://example.com/subscription#My%20VPN%2BOne", https?.url)
        assertEquals(DefaultDesktopSubscriptionUserAgent, https?.userAgent)

        assertEquals(DesktopSubscriptionInstallSource.RawHttp, http?.source)
        assertEquals("import sub", http?.name)
        assertEquals("http://example.com/subscription", http?.url)
    }

    @Test
    fun parses_supported_client_install_schemes_with_source_user_agents_and_names() {
        val v2rayNg = DesktopSubscriptionInstallUri.parseOrNull(
            "v2rayng://install-config?url=https%3A%2F%2Fexample.com%2Fv2&name=V2Ray%20Group",
        )
        val clash = DesktopSubscriptionInstallUri.parseOrNull(
            "clash://install-sub?url=https%3A%2F%2Fexample.com%2Fclash#Clash%20Group",
        )
        val clashMeta = DesktopSubscriptionInstallUri.parseOrNull(
            "CLASHMETA://INSTALL-CONFIG?url=https%3A%2F%2Fexample.com%2Fmeta",
        )
        val flClashX = DesktopSubscriptionInstallUri.parseOrNull(
            "flclashx://install-config?url=https%3A%2F%2Fexample.com%2Fflclash",
        )

        assertEquals(DesktopSubscriptionInstallSource.V2rayNg, v2rayNg?.source)
        assertEquals("V2Ray Group", v2rayNg?.name)
        assertEquals("https://example.com/v2", v2rayNg?.url)
        assertEquals(DefaultDesktopSubscriptionUserAgent, v2rayNg?.userAgent)

        assertEquals(DesktopSubscriptionInstallSource.Clash, clash?.source)
        assertEquals("Clash Group", clash?.name)
        assertEquals("clash.meta", clash?.userAgent)

        assertEquals(DesktopSubscriptionInstallSource.ClashMeta, clashMeta?.source)
        assertEquals("clashsub", clashMeta?.name)
        assertEquals("clash.meta", clashMeta?.userAgent)

        assertEquals(DesktopSubscriptionInstallSource.FlClashX, flClashX?.source)
        assertEquals("clashsub", flClashX?.name)
        assertEquals("FlClash X/v0.4.2 Platform/android", flClashX?.userAgent)
    }

    @Test
    fun rejects_untrusted_or_malformed_install_links() {
        assertNull(DesktopSubscriptionInstallUri.parseOrNull("v2rayng://other-host?url=https%3A%2F%2Fexample.com%2Fsub"))
        assertNull(DesktopSubscriptionInstallUri.parseOrNull("v2rayng://install-config?url=file%3A%2F%2F%2Fsecret"))
        assertNull(DesktopSubscriptionInstallUri.parseOrNull("ftp://example.com/sub"))
        assertNull(DesktopSubscriptionInstallUri.parseOrNull("https://example.com/a path"))
        assertNull(DesktopSubscriptionInstallUri.parseOrNull("clash://install-config?name=MissingUrl"))
    }
}
