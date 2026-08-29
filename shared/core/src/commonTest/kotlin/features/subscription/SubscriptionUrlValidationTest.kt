// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionUrlValidationTest {
    @Test
    fun manual_urls_allow_http_and_https_but_install_urls_require_https() {
        assertTrue("https://example.com/sub".isValidManualSubscriptionUrl())
        assertTrue("http://example.com/sub".isValidManualSubscriptionUrl())
        assertTrue("https://example.com/sub".isValidSubscriptionInstallUrl())
        assertFalse("http://example.com/sub".isValidSubscriptionInstallUrl())
    }

    @Test
    fun rejects_malformed_or_unsafe_urls() {
        assertFalse("https://".isValidManualSubscriptionUrl())
        assertFalse("https://example.com/a path".isValidManualSubscriptionUrl())
        assertFalse("file:///local/sub".isValidManualSubscriptionUrl())
        assertTrue("http://example.com/sub".isPlainHttpSubscriptionUrl())
        assertFalse("https://example.com/sub".isPlainHttpSubscriptionUrl())
    }
}
