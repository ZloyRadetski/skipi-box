// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.io.IOException
import java.net.http.HttpTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionRefreshFailurePolicyHostConnectionTest {
    @Test
    fun hostClassifiesItsConnectionTimeoutAcrossTheCauseChainWithoutRetryingGenericIo() {
        val timeout = HttpTimeoutException("request timed out")
        val wrappedTimeout = IllegalStateException("refresh failed", timeout)

        assertTrue(classify(timeout))
        assertTrue(classify(wrappedTimeout))
        assertFalse(classify(IOException("unclassified host I/O failure")))
        assertFalse(classify(IllegalArgumentException("invalid payload")))
    }

    private fun classify(error: Throwable): Boolean = isTransientSubscriptionRefreshFailure(
        error = error,
        httpStatusCode = { null },
        isHostConnectionFailure = { cause -> cause is HttpTimeoutException },
    )
}
