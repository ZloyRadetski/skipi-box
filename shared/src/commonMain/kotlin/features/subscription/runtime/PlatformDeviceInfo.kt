// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

expect object PlatformDeviceInfo {
    val osName: String
    val osVersion: String
    val deviceModel: String
}

fun subscriptionDeviceHeaders(installationHwid: String): Map<String, String> {
    return linkedMapOf(
        "x-client" to "SKIPI",
        "x-app-version" to app.SharedProjectInfo.VERSION_NAME,
        "x-device-os" to PlatformDeviceInfo.osName,
        "x-ver-os" to PlatformDeviceInfo.osVersion,
        "x-device-model" to PlatformDeviceInfo.deviceModel,
        "x-hwid" to installationHwid,
        "X-Device-ID" to installationHwid,
    )
}
