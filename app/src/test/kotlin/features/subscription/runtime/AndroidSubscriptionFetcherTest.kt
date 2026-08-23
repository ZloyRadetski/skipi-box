// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import engine.proxy.LocalProxyOptions
import engine.proxy.LocalProxyRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidSubscriptionFetcherTest {
    @Test
    fun disabledUpdateViaProxyUsesNoLocalSocksProxy() {
        LocalProxyRuntime.update(
            LocalProxyOptions(
                listenAddress = "127.0.0.1",
                port = 10808,
                username = "user",
                password = "password",
            ),
        )
        try {
            assertNull(AndroidSubscriptionFetchOptions(useRunningProxy = false).toProxy())

            val enabledProxy = checkNotNull(AndroidSubscriptionFetchOptions(useRunningProxy = true).toProxy())
            assertEquals(10808, enabledProxy.port)
            assertEquals("user", enabledProxy.username)
        } finally {
            LocalProxyRuntime.clear()
        }
    }
}
