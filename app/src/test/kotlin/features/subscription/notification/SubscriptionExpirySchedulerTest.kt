// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.AppState
import app.SubscriptionGroupState
import features.subscription.ExpiryReminderUnit
import features.subscription.SubscriptionExpiryReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubscriptionExpirySchedulerTest {
    @Test
    fun android_baseline_schedules_earliest_future_trigger_from_eligible_groups_and_overrides() {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val atExpiration = SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration)
        val state = AppState(
            subscriptionGroups = listOf(
                expiryGroup(id = 1, expireAtSeconds = nowSeconds + 20 * DaySeconds),
                expiryGroup(
                    id = 2,
                    expireAtSeconds = nowSeconds + 5 * DaySeconds,
                    customReminders = listOf(atExpiration),
                ),
                expiryGroup(
                    id = 3,
                    expireAtSeconds = nowSeconds + 2 * DaySeconds,
                    enabled = false,
                    customReminders = listOf(atExpiration),
                ),
                expiryGroup(
                    id = 4,
                    expireAtSeconds = nowSeconds + DaySeconds,
                    notifyOnExpiry = false,
                    customReminders = listOf(atExpiration),
                ),
                expiryGroup(
                    id = 5,
                    expireAtSeconds = nowSeconds + 4 * DaySeconds,
                    customReminders = emptyList(),
                ),
                expiryGroup(
                    id = 6,
                    expireAtSeconds = 0L,
                    customReminders = listOf(atExpiration),
                ),
            ),
            subscriptionExpiryReminders = listOf(
                SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days),
                SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days),
            ),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()

        SubscriptionExpiryScheduler.schedule(context, state)

        val alarmManager = checkNotNull(context.getSystemService(AlarmManager::class.java))
        val scheduledAlarm = checkNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertEquals(AlarmManager.RTC_WAKEUP, scheduledAlarm.type)
        assertEquals((nowSeconds + 5 * DaySeconds) * 1000L, scheduledAlarm.triggerAtTime)
    }
}

private const val DaySeconds = 24 * 60 * 60L

private fun expiryGroup(
    id: Int,
    expireAtSeconds: Long,
    enabled: Boolean = true,
    notifyOnExpiry: Boolean = true,
    customReminders: List<SubscriptionExpiryReminder>? = null,
) = SubscriptionGroupState(
    id = id,
    name = "Group $id",
    url = "https://example.test/$id",
    userAgent = "",
    updateInterval = "",
    enabled = enabled,
    trafficExpireAtSeconds = expireAtSeconds,
    notifyOnExpiry = notifyOnExpiry,
    customExpiryReminders = customReminders,
)
