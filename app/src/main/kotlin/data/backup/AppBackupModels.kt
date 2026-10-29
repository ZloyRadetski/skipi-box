// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import features.backup.AppBackupFile
import features.backup.AppBackupWarning
internal data class AppBackupRestorePreview(
    val backup: AppBackupFile,
    val restoredState: AppState,
    val warnings: List<AppBackupWarning>,
) {
    val subscriptionGroupCount: Int
        get() = restoredState.subscriptionGroups.size

    val proxyServerCount: Int
        get() = restoredState.proxyServers.size

    val routeRuleCount: Int
        get() = restoredState.routeRules.size

    val trafficConfigCount: Int
        get() = restoredState.trafficConfigs.size
}

