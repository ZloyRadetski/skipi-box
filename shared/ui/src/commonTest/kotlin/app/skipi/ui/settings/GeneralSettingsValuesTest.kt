// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class GeneralSettingsValuesTest {
    @Test
    fun trafficRefreshIntervalClampsPersistedValuesToSliderRange() {
        assertEquals(1, GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(Int.MIN_VALUE))
        assertEquals(1, GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(1))
        assertEquals(6, GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(6))
        assertEquals(10, GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(10))
        assertEquals(10, GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(Int.MAX_VALUE))
    }
}
