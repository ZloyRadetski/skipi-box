// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.backup

import features.config.TrafficConfigState
import features.routing.model.RouteRule
import features.settings.servicecontrol.ServiceControlSettings
import features.settings.servicecontrol.ServiceControlSchedule
import features.settings.servicecontrol.ServiceControlWifi
import features.settings.servicecontrol.ServiceControlWifiRule

fun ServiceControlSettings.toAppBackupServiceControl(): AppBackupServiceControl =
    AppBackupServiceControl(
        enabled = enabled,
        schedule = AppBackupServiceControlSchedule(
            enabled = schedule.enabled,
            startCron = schedule.startCron,
            stopCron = schedule.stopCron,
        ),
        wifi = AppBackupServiceControlWifi(
            enabled = wifi.enabled,
            connectStart = wifi.connectStart.toAppBackupServiceControlWifiRule(),
            connectStop = wifi.connectStop.toAppBackupServiceControlWifiRule(),
            disconnectStart = wifi.disconnectStart.toAppBackupServiceControlWifiRule(),
            disconnectStop = wifi.disconnectStop.toAppBackupServiceControlWifiRule(),
        ),
    )

fun AppBackupServiceControl.toServiceControlSettings(): ServiceControlSettings =
    ServiceControlSettings(
        enabled = enabled,
        schedule = ServiceControlSchedule(
            enabled = schedule.enabled,
            startCron = schedule.startCron,
            stopCron = schedule.stopCron,
        ),
        wifi = ServiceControlWifi(
            enabled = wifi.enabled,
            connectStart = wifi.connectStart.toServiceControlWifiRule(),
            connectStop = wifi.connectStop.toServiceControlWifiRule(),
            disconnectStart = wifi.disconnectStart.toServiceControlWifiRule(),
            disconnectStop = wifi.disconnectStop.toServiceControlWifiRule(),
        ),
    )

private fun ServiceControlWifiRule.toAppBackupServiceControlWifiRule() =
    AppBackupServiceControlWifiRule(enabled = enabled, ssids = ssids, bssids = bssids)

private fun AppBackupServiceControlWifiRule.toServiceControlWifiRule() =
    ServiceControlWifiRule(enabled = enabled, ssids = ssids, bssids = bssids)

fun RouteRule.toAppBackupRouteRule(): AppBackupRouteRule =
    AppBackupRouteRule(
        id = id,
        remarks = remarks,
        outboundTag = outboundTag,
        domain = domain,
        ip = ip,
        process = process,
        port = port,
        protocol = protocol,
        network = network,
        enabled = enabled,
    )

fun AppBackupRouteRule.toRouteRule(): RouteRule =
    RouteRule(
        id = id,
        remarks = remarks,
        outboundTag = outboundTag,
        domain = domain,
        ip = ip,
        process = process,
        port = port,
        protocol = protocol,
        network = network,
        enabled = enabled,
    )

fun TrafficConfigState.toAppBackupTrafficConfig(): AppBackupTrafficConfig =
    AppBackupTrafficConfig(
        id = id,
        name = name,
        rawConfig = rawConfig,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        autoUpdate = autoUpdate,
        updateInterval = updateInterval,
        proxyAppListMode = proxyAppListMode,
        proxyAppListSelectedApps = proxyAppListSelectedApps,
        androidSettings = androidSettings,
        networkActivation = networkActivation,
        resourceSettings = resourceSettings,
    )

fun AppBackupTrafficConfig.toTrafficConfigState(): TrafficConfigState =
    TrafficConfigState(
        id = id,
        name = name,
        rawConfig = rawConfig,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        autoUpdate = autoUpdate,
        updateInterval = updateInterval,
        proxyAppListMode = proxyAppListMode,
        proxyAppListSelectedApps = proxyAppListSelectedApps,
        androidSettings = androidSettings,
        networkActivation = networkActivation,
        resourceSettings = resourceSettings,
    )
