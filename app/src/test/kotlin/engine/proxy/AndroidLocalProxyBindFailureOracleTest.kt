// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Baseline for the current Android cause-chain classifier; this is not a VPN/native start test. */
class AndroidLocalProxyBindFailureOracleTest {
    @Test
    fun recognizesEachCurrentAndroidBindMarkerAndNestedCauses() {
        assertTrue(IllegalStateException("Address already in use").isLocalPortBindFailure())
        assertTrue(IllegalStateException("failed to listen TCP 127.0.0.1:20001").isLocalPortBindFailure())
        assertTrue(
            IllegalStateException(
                "Core start failed",
                IllegalArgumentException("LISTEN TCP 127.0.0.1:20001"),
            ).isLocalPortBindFailure(),
        )
    }

    @Test
    fun unrelatedBindLikeMessagesDoNotMatchCurrentAndroidClassifier() {
        assertFalse(IllegalStateException("connection refused").isLocalPortBindFailure())
        assertFalse(IllegalStateException("proxy port is occupied").isLocalPortBindFailure())
        assertFalse(
            IllegalStateException(
                "Core start failed",
                IllegalArgumentException("invalid profile JSON"),
            ).isLocalPortBindFailure(),
        )
    }
}
