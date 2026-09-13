// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateCheckStoreTest {
    @Test
    fun automatic_check_is_due_only_after_the_ttl() {
        val now = 1_000_000_000L
        assertTrue(isAppUpdateCheckDue(lastCheckMillis = 0L, nowMillis = now))
        assertFalse(
            isAppUpdateCheckDue(
                lastCheckMillis = now - AppUpdateCheckTtlMillis + 1L,
                nowMillis = now,
            ),
        )
        assertTrue(
            isAppUpdateCheckDue(
                lastCheckMillis = now - AppUpdateCheckTtlMillis,
                nowMillis = now,
            ),
        )
    }
}
