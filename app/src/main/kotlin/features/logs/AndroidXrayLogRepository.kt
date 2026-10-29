// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context

internal object AndroidCoreLogRepository : XrayFileLogRepository(
    logTag = "AndroidCoreLogRepository",
    onFailure = { message, error -> AndroidAppLogger.warn("AndroidCoreLogRepository", message, error) },
) {
    fun initialize(context: Context, retentionDays: Int = 0) {
        initialize(context.applicationContext.androidXrayErrorLog(), retentionDays)
    }
}

internal object AndroidAccessLogRepository : XrayFileLogRepository(
    logTag = "AndroidAccessLogRepository",
    onFailure = { message, error -> AndroidAppLogger.warn("AndroidAccessLogRepository", message, error) },
) {
    fun initialize(context: Context, retentionDays: Int = 0) {
        initialize(context.applicationContext.androidXrayAccessLog(), retentionDays)
    }
}
