// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import android.content.Context
import app.ExpiryReminderUnit
import app.R
import app.SubscriptionExpiryReminder
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun SubscriptionExpiryReminder.formatLabel(context: Context): String {
    return when (unit) {
        ExpiryReminderUnit.AtExpiration -> context.getString(R.string.subscription_expiry_reminder_format_at_expiration)
        ExpiryReminderUnit.Minutes -> context.getString(R.string.subscription_expiry_reminder_format_minutes, value)
        ExpiryReminderUnit.Hours -> context.getString(R.string.subscription_expiry_reminder_format_hours, value)
        ExpiryReminderUnit.Days -> context.getString(R.string.subscription_expiry_reminder_format_days, value)
        ExpiryReminderUnit.Weeks -> context.getString(R.string.subscription_expiry_reminder_format_weeks, value)
    }
}

internal fun formatRemainingDuration(context: Context, seconds: Long): String {
    if (seconds <= 0L) return context.getString(R.string.subscription_expiry_reminder_format_at_expiration)
    val minutes = seconds / 60L
    val hours = seconds / 3600L
    val days = seconds / 86400L
    val weeks = seconds / (7 * 86400L)

    return when {
        weeks > 0 && seconds % (7 * 86400L) == 0L -> context.getString(R.string.subscription_expiry_duration_weeks, weeks)
        days > 0 && seconds % 86400L == 0L -> context.getString(R.string.subscription_expiry_duration_days, days)
        hours > 0 && seconds % 3600L == 0L -> context.getString(R.string.subscription_expiry_duration_hours, hours)
        days > 0 -> context.getString(R.string.subscription_expiry_duration_days, days)
        hours > 0 -> context.getString(R.string.subscription_expiry_duration_hours, hours)
        else -> context.getString(R.string.subscription_expiry_duration_minutes, minutes.coerceAtLeast(1))
    }
}

internal fun formatExpiryDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return ""
    val date = Date(timestampSeconds * 1000L)
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    return formatter.format(date)
}
