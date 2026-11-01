// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.AppState
import app.SubscriptionGroupState
import features.subscription.usecase.toSubscriptionFetchOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDeviceHeadersOptionsTest {
    private fun group(deviceHeaders: String) = SubscriptionGroupState(
        id = 1,
        name = "Group",
        url = "https://sub.example/list",
        userAgent = "",
        updateInterval = "24",
        deviceHeaders = deviceHeaders,
        enabled = true,
    )

    @Test
    fun aGroupSwitchedOnSendsTheHeadersEvenWhenTheGlobalSettingIsOff() {
        val state = AppState(enableSubscriptionDeviceHeaders = false)
        assertTrue(state.toSubscriptionFetchOptions(group("on")).sendDeviceHeaders)
    }

    @Test
    fun aGroupSwitchedOffSuppressesTheHeadersEvenWhenTheGlobalSettingIsOn() {
        val state = AppState(enableSubscriptionDeviceHeaders = true)
        assertFalse(state.toSubscriptionFetchOptions(group("off")).sendDeviceHeaders)
    }

    @Test
    fun aGroupWithoutAChoiceFollowsTheGlobalSetting() {
        assertTrue(AppState(enableSubscriptionDeviceHeaders = true).toSubscriptionFetchOptions(group("")).sendDeviceHeaders)
        assertFalse(AppState(enableSubscriptionDeviceHeaders = false).toSubscriptionFetchOptions(group("")).sendDeviceHeaders)
    }
}
