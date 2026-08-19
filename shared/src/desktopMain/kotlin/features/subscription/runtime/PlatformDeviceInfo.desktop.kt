// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

actual object PlatformDeviceInfo {
    actual val osName: String = System.getProperty("os.name") ?: "Desktop"
    actual val osVersion: String = System.getProperty("os.version") ?: "unknown"
    actual val deviceModel: String = System.getProperty("user.name") ?: "PC"
}
