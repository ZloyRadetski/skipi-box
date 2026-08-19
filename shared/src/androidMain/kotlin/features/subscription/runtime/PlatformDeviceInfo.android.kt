// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.os.Build

actual object PlatformDeviceInfo {
    actual val osName: String = "Android"
    actual val osVersion: String = Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" }
    actual val deviceModel: String = Build.MODEL.orEmpty().ifBlank { "Android" }
}
