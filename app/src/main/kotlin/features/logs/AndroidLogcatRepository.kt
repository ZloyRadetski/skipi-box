// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context

internal object AndroidLogcatRepository : PersistedAppLogRepository(
    logTag = "AndroidLogcatRepository",
    onFailure = { message, error -> AndroidAppLogger.platformWarn("AndroidLogcatRepository", message, error) },
) {
    fun initialize(context: Context, retentionDays: Int = 0) {
        initialize(context.applicationContext.androidAppLogcatFile(), retentionDays)
    }
}
