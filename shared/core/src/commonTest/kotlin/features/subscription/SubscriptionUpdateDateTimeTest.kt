// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionUpdateDateTimeTest {
    @Test
    fun timestampFormatsAsLocalizedDateAndTime() {
        val formatted = formatSubscriptionUpdateTimestamp(1_234L)

        assertTrue(formatted.isNotBlank())
        assertFalse(formatted == "1,234")
        assertFalse(formatted == "1234")
    }
}
