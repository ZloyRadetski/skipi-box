// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings.servicecontrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceControlValidationTest {
    @Test
    fun parsesPortableCronExpressionsAndRejectsUnsafeRanges() {
        assertEquals(ServiceCronParseResult.Valid("*/15 8-20 * * 1-5"), parseServiceCron("  */15 8-20 * * 1-5  "))
        assertEquals(
            ServiceCronParseResult.Invalid(ServiceCronError.OutOfRange),
            parseServiceCron("0 24 * * *"),
        )
        assertEquals(
            ServiceCronParseResult.Invalid(ServiceCronError.InvalidStep),
            parseServiceCron("*/0 * * * *"),
        )
    }

    @Test
    fun normalizesWifiIdentifiersWithoutJvmCharsets() {
        assertEquals("aa:bb:cc:dd:ee:ff", normalizeBssidOrNull("AA-BB-CC-DD-EE-FF"))
        assertEquals(null, normalizeBssidOrNull("aa:bb:cc"))
        assertTrue(isValidServiceSsid("я".repeat(16)))
        assertFalse(isValidServiceSsid("я".repeat(17)))
    }

    @Test
    fun normalizesListsAndKeepsMutuallyExclusiveWifiActions() {
        val normalized = normalizeServiceControlSettings(
            ServiceControlSettings(
                schedule = ServiceControlSchedule(startCron = " 0 8 * * * ", stopCron = " 0 20 * * * "),
                wifi = ServiceControlWifi(
                    connectStart = ServiceControlWifiRule(
                        enabled = true,
                        ssids = listOf("Home", "Home", ""),
                        bssids = listOf("AA-BB-CC-DD-EE-FF", "aa:bb:cc:dd:ee:ff"),
                    ),
                    connectStop = ServiceControlWifiRule(enabled = true),
                ),
            ),
        )

        assertEquals("0 8 * * *", normalized.schedule.startCron)
        assertEquals(listOf("Home"), normalized.wifi.connectStart.ssids)
        assertEquals(listOf("aa:bb:cc:dd:ee:ff"), normalized.wifi.connectStart.bssids)
        assertFalse(normalized.wifi.connectStart.enabled)
        assertTrue(normalized.wifi.connectStop.enabled)
    }

    @Test
    fun preventsSavingEnabledDraftWithInvalidCronOrPendingWifiEditor() {
        val invalidCron = ServiceControlSettings(
            enabled = true,
            schedule = ServiceControlSchedule(enabled = true, startCron = "bad", stopCron = "0 1 * * *"),
        )
        val pendingWifiEdit = ServiceControlSettings(
            enabled = true,
            wifi = ServiceControlWifi(enabled = true),
        )

        assertFalse(canSaveServiceControlDraft(invalidCron, hasPendingEditor = false))
        assertFalse(canSaveServiceControlDraft(pendingWifiEdit, hasPendingEditor = true))
    }
}
