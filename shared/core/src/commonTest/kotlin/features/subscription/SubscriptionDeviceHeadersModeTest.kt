// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionDeviceHeadersModeTest {
    @Test
    fun theSubscriptionSettingBeatsTheGlobalOneBothWays() {
        // Global off, subscription on: the headers are sent.
        assertEquals(true, resolveSubscriptionDeviceHeaders(override = "on", globalEnabled = false))
        // Global on, subscription off: they are not.
        assertEquals(false, resolveSubscriptionDeviceHeaders(override = "off", globalEnabled = true))
        assertEquals(true, resolveSubscriptionDeviceHeaders(override = "on", globalEnabled = true))
        assertEquals(false, resolveSubscriptionDeviceHeaders(override = "off", globalEnabled = false))
    }

    @Test
    fun withoutAnOverrideTheGlobalSettingDecides() {
        assertEquals(true, resolveSubscriptionDeviceHeaders(override = "", globalEnabled = true))
        assertEquals(false, resolveSubscriptionDeviceHeaders(override = "", globalEnabled = false))
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheGlobalSetting() {
        // An old build stored free text in the neighbouring HWID column; it must never switch anything.
        assertEquals(true, resolveSubscriptionDeviceHeaders(override = "saved-device", globalEnabled = true))
        assertEquals(false, resolveSubscriptionDeviceHeaders(override = "saved-device", globalEnabled = false))
    }

    @Test
    fun theStoredValueIgnoresCaseAndSurroundingSpaces() {
        assertEquals(SubscriptionDeviceHeadersMode.Enabled, SubscriptionDeviceHeadersMode.fromSerialized(" ON "))
        assertEquals(SubscriptionDeviceHeadersMode.Disabled, SubscriptionDeviceHeadersMode.fromSerialized("Off"))
        assertEquals(SubscriptionDeviceHeadersMode.Default, SubscriptionDeviceHeadersMode.fromSerialized(null))
    }

    @Test
    fun everyModeSurvivesSerialization() {
        SubscriptionDeviceHeadersMode.entries.forEach { mode ->
            assertEquals(mode, SubscriptionDeviceHeadersMode.fromSerialized(mode.serialized))
        }
    }
}
