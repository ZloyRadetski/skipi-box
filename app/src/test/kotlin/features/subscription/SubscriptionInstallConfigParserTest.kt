// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionInstallConfigParserTest {
    @Test
    fun parser_keeps_android_https_policy_and_source_user_agents() {
        val direct = "https://example.com/sub#My%20VPN".toSubscriptionInstallConfigOrNull()
        val v2rayNg = (
            "v2rayng://install-config?url=https%3A%2F%2Fexample.com%2Fsub" +
                "&name=Imported%20group"
            ).toSubscriptionInstallConfigOrNull()
        val clash = "clash://install-sub?url=https%3A%2F%2Fexample.com%2Fsub".toSubscriptionInstallConfigOrNull()

        assertEquals("My VPN", direct?.name)
        assertEquals(DefaultSubscriptionUserAgent, direct?.userAgent)
        assertEquals("Imported group", v2rayNg?.name)
        assertEquals(DefaultSubscriptionUserAgent, v2rayNg?.userAgent)
        assertEquals(ClashMetaSubscriptionUserAgent, clash?.userAgent)
    }

    @Test
    fun parser_rejects_http_direct_and_embedded_subscription_urls() {
        assertNull("http://example.com/sub".toSubscriptionInstallConfigOrNull())
        assertNull(
            "v2rayng://install-config?url=http%3A%2F%2Fexample.com%2Fsub".toSubscriptionInstallConfigOrNull(),
        )
    }
}
