// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
typealias AppBackupCustomResourceFile = features.backup.AppBackupCustomResourceFile
typealias AppBackupData = features.backup.AppBackupData
typealias AppBackupFile = features.backup.AppBackupFile
typealias AppBackupProxyServer = features.backup.AppBackupProxyServer
typealias AppBackupRouteRule = features.backup.AppBackupRouteRule
typealias AppBackupServiceControl = features.backup.AppBackupServiceControl
typealias AppBackupServiceControlSchedule = features.backup.AppBackupServiceControlSchedule
typealias AppBackupServiceControlWifi = features.backup.AppBackupServiceControlWifi
typealias AppBackupServiceControlWifiRule = features.backup.AppBackupServiceControlWifiRule
typealias AppBackupSettings = features.backup.AppBackupSettings
typealias AppBackupSubscriptionGroup = features.backup.AppBackupSubscriptionGroup
typealias AppBackupTrafficConfig = features.backup.AppBackupTrafficConfig

internal const val AppBackupFormat = features.backup.AppBackupFormat
internal const val CurrentAppBackupVersion = features.backup.CurrentAppBackupVersion

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

internal sealed interface AppBackupWarning {
    data class MissingChainProxyMembers(val count: Int) : AppBackupWarning

    data class MissingStrategyGroupMembers(val count: Int) : AppBackupWarning
}
