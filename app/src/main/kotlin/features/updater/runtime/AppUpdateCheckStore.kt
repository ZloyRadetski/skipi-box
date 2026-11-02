// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import features.updater.AppUpdateChannel
import features.updater.isAppUpdateAutomaticAttemptDue
import features.updater.isAppUpdateCheckDue
import androidx.core.content.edit

/**
 * Small persistent cache used only by automatic release checks. Each channel keeps its own ETag
 * and TTL, so a 304 or a recent check on one channel never hides a release of another.
 */
internal class AppUpdateCheckStore(context: Context, channel: AppUpdateChannel = AppUpdateChannel.Stable) {
    private val preferences = context.applicationContext.getSharedPreferences(
        // Stable keeps the original file, so installs from before channels carry their cache over.
        if (channel == AppUpdateChannel.Stable) PreferencesName else "${PreferencesName}_${channel.id}",
        Context.MODE_PRIVATE,
    )

    /** Forgets the ETag and the TTL so the next check is due and asks GitHub in full. */
    fun invalidate() {
        preferences.edit { clear() }
    }

    fun isDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return isAppUpdateCheckDue(
            lastCheckMillis = preferences.getLong(KeyLastCheckMillis, 0L),
            nowMillis = nowMillis,
        )
    }

    fun eTag(): String? = preferences.getString(KeyETag, null)?.takeIf(String::isNotBlank)

    fun isAutomaticAttemptDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return isAppUpdateAutomaticAttemptDue(
            lastAttemptMillis = preferences.getLong(KeyLastAttemptMillis, 0L),
            nowMillis = nowMillis,
        )
    }

    fun recordSuccessfulCheck(
        eTag: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        preferences.edit {
            putLong(KeyLastCheckMillis, nowMillis)
            eTag?.takeIf(String::isNotBlank)?.let { value ->
                putString(KeyETag, value)
            } ?: remove(KeyETag)
        }
    }

    /** Records diagnostics only; a failed request must never advance the successful-check TTL. */
    fun recordAttempt(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KeyLastAttemptMillis, nowMillis) }
    }
}

private const val PreferencesName = "app_update_check"
private const val KeyLastCheckMillis = "last_check_millis"
private const val KeyLastAttemptMillis = "last_attempt_millis"
private const val KeyETag = "latest_release_etag"
