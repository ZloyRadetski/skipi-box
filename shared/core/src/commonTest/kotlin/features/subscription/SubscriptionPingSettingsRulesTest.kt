// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionPingSettingsRulesTest {
    @Test
    fun validatesHttpUrlsAndTimeoutBounds() {
        assertTrue(SubscriptionPingSettingsRules.isValidHttpUrl("https://example.org/path?q=1"))
        assertTrue(SubscriptionPingSettingsRules.isValidHttpUrl("http://[::1]:8080/"))
        assertFalse(SubscriptionPingSettingsRules.isValidHttpUrl("file:///etc/passwd"))
        assertFalse(SubscriptionPingSettingsRules.isValidHttpUrl("https://:443/path"))
        assertFalse(SubscriptionPingSettingsRules.isValidHttpUrl("https://example.org:abc"))
        assertTrue(SubscriptionPingSettingsRules.isValidTimeoutMillis("500"))
        assertTrue(SubscriptionPingSettingsRules.isValidTimeoutMillis("60000"))
        assertFalse(SubscriptionPingSettingsRules.isValidTimeoutMillis("499"))
        assertFalse(SubscriptionPingSettingsRules.isValidTimeoutMillis("60001"))
    }
}
