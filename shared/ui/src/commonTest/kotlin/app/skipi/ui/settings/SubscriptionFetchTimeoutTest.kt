// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionFetchTimeoutTest {
    @Test
    fun theSliderCoversOneToSixtySecondsAndOlderValuesAreBroughtInside() {
        assertEquals(1, SubscriptionFetchTimeout.Min)
        assertEquals(60, SubscriptionFetchTimeout.Max)
        assertEquals(10, SubscriptionFetchTimeout.normalize(10))
        assertEquals(60, SubscriptionFetchTimeout.normalize(120))
        assertEquals(1, SubscriptionFetchTimeout.normalize(0))
    }
}
