// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionInstallUriTest {
    @Test
    fun direct_urls_follow_the_platform_scheme_policy() {
        val https = parseSubscriptionInstallUriOrNull(
            value = "https://example.com/sub#My%20VPN%2BOne",
            policy = SecureOnlyPolicy,
        )
        val httpForSecurePlatform = parseSubscriptionInstallUriOrNull(
            value = "http://example.com/sub",
            policy = SecureOnlyPolicy,
        )
        val httpForDesktop = parseSubscriptionInstallUriOrNull(
            value = "http://example.com/sub",
            policy = DesktopCompatiblePolicy,
        )

        assertEquals(SubscriptionInstallSource.RawHttp, https?.source)
        assertEquals("My VPN+One", https?.name)
        assertEquals("https://example.com/sub#My%20VPN%2BOne", https?.url)
        assertNull(httpForSecurePlatform)
        assertEquals(SubscriptionInstallSource.RawHttp, httpForDesktop?.source)
        assertEquals("import sub", httpForDesktop?.name)
    }

    @Test
    fun client_links_preserve_source_and_prefer_explicit_names() {
        val v2rayNg = parseSubscriptionInstallUriOrNull(
            value = "v2rayng://install-config?url=https%3A%2F%2Fexample.com%2Fv2&name=V2Ray%20Group",
            policy = SecureOnlyPolicy,
        )
        val clash = parseSubscriptionInstallUriOrNull(
            value = "clash://install-sub?url=https%3A%2F%2Fexample.com%2Fclash#Clash%20Group",
            policy = SecureOnlyPolicy,
        )
        val clashMeta = parseSubscriptionInstallUriOrNull(
            value = "CLASHMETA://INSTALL-CONFIG?url=https%3A%2F%2Fexample.com%2Fmeta",
            policy = SecureOnlyPolicy,
        )

        assertEquals(SubscriptionInstallSource.V2rayNg, v2rayNg?.source)
        assertEquals("V2Ray Group", v2rayNg?.name)
        assertEquals("https://example.com/v2", v2rayNg?.url)
        assertEquals(SubscriptionInstallSource.Clash, clash?.source)
        assertEquals("Clash Group", clash?.name)
        assertEquals(SubscriptionInstallSource.ClashMeta, clashMeta?.source)
        assertEquals("clashsub", clashMeta?.name)
    }

    @Test
    fun rejects_untrusted_or_invalid_links_and_identifies_known_custom_uris() {
        assertNull(
            parseSubscriptionInstallUriOrNull(
                value = "v2rayng://other-host?url=https%3A%2F%2Fexample.com%2Fsub",
                policy = SecureOnlyPolicy,
            ),
        )
        assertNull(
            parseSubscriptionInstallUriOrNull(
                value = "v2rayng://install-config?url=http%3A%2F%2Fexample.com%2Fsub",
                policy = SecureOnlyPolicy,
            ),
        )
        assertNull(
            parseSubscriptionInstallUriOrNull(
                value = "clash://install-config?name=MissingUrl",
                policy = SecureOnlyPolicy,
            ),
        )

        assertTrue("v2rayng://install-config?url=https%3A%2F%2Fexample.com%2Fsub".isSubscriptionInstallUri())
        assertFalse("v2rayng://other-host?url=https%3A%2F%2Fexample.com%2Fsub".isSubscriptionInstallUri())
        assertFalse("https://example.com/sub".isSubscriptionInstallUri())
    }

    private companion object {
        val SecureOnlyPolicy = SubscriptionInstallUriParsingPolicy(
            directUrlPolicy = SubscriptionInstallUrlPolicy.HttpsOnly,
            embeddedUrlPolicy = SubscriptionInstallUrlPolicy.HttpsOnly,
        )
        val DesktopCompatiblePolicy = SubscriptionInstallUriParsingPolicy(
            directUrlPolicy = SubscriptionInstallUrlPolicy.HttpOrHttps,
            embeddedUrlPolicy = SubscriptionInstallUrlPolicy.HttpOrHttps,
        )
    }
}
