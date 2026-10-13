// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeServerTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshBatchTest {
    @Test
    fun refreshAllEffectCallsTheConfiguredBatchRefreshExactlyOnce() {
        var calls = 0
        val context = DesktopProxyHomeEffectContext(
            subscriptions = listOf(DesktopStoredSubscription(id = 7, url = "https://example.test/sub")),
            onRefreshAllSubscriptions = {
                calls += 1
                Result.success(Unit)
            },
        )

        val result = context.handle(ProxyHomeEffect.RefreshAllSubscriptions)

        assertTrue(result.isSuccess)
        assertEquals(1, calls)
    }

    @Test
    fun missingBatchRefreshCallbackIsRejectedInsteadOfReturningFalseSuccess() {
        val context = DesktopProxyHomeEffectContext(
            subscriptions = listOf(DesktopStoredSubscription(id = 7, url = "https://example.test/sub")),
            onRefreshAllSubscriptions = null,
        )
        val result = context.handle(ProxyHomeEffect.RefreshAllSubscriptions)

        assertTrue(result.isFailure)
        assertIs<UnsupportedOperationException>(result.exceptionOrNull())
    }

    @Test
    fun serverToolAndHomeEffectUseTheSameBatchRefreshCallback() {
        var calls = 0
        val context = DesktopProxyHomeEffectContext(
            subscriptions = listOf(DesktopStoredSubscription(id = 7, url = "https://example.test/sub")),
            onRefreshAllSubscriptions = { calls += 1 },
        )

        assertTrue(context.handle(ProxyHomeEffect.RefreshAllSubscriptions).isSuccess)
        assertTrue(context.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.UpdateSubscriptions)).isSuccess)

        assertEquals(2, calls)
    }
}
