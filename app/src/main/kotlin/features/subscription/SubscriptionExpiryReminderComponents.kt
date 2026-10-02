// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.SubscriptionExpiryReminder
import app.skipi.ui.subscription.SubscriptionExpiryReminderList as SharedSubscriptionExpiryReminderList

@Composable
internal fun SubscriptionExpiryReminderList(
    reminders: List<SubscriptionExpiryReminder>,
    onRemindersChange: (List<SubscriptionExpiryReminder>) -> Unit,
    modifier: Modifier = Modifier,
) = SharedSubscriptionExpiryReminderList(
    reminders = reminders,
    onRemindersChange = onRemindersChange,
    modifier = modifier,
)
