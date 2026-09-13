// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.core.content.edit

/** Small persistent cache used only by automatic release checks. */
internal class AppUpdateCheckStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun isDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return isAppUpdateCheckDue(
            lastCheckMillis = preferences.getLong(KeyLastCheckMillis, 0L),
            nowMillis = nowMillis,
        )
    }

    fun eTag(): String? = preferences.getString(KeyETag, null)?.takeIf(String::isNotBlank)

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

    /** A failed automatic request still counts as a check for TTL throttling. */
    fun recordAttempt(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KeyLastCheckMillis, nowMillis) }
    }
}

internal fun isAppUpdateCheckDue(
    lastCheckMillis: Long,
    nowMillis: Long,
): Boolean {
    return lastCheckMillis <= 0L ||
        nowMillis - lastCheckMillis >= AppUpdateCheckTtlMillis
}

internal const val AppUpdateCheckTtlMillis = 24L * 60L * 60L * 1_000L

private const val PreferencesName = "app_update_check"
private const val KeyLastCheckMillis = "last_check_millis"
private const val KeyETag = "latest_release_etag"
