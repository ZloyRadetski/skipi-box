// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

/**
 * Build metadata for the shared (multiplatform) code.
 *
 * Kept separate from the Android-generated [ProjectInfo] to avoid a
 * duplicate-class conflict; values mirror ProjectConfig — update together
 * when bumping versions.
 */
object SharedProjectInfo {
    const val PROJECT_NAME = "SKIPI"
    const val VERSION_NAME = "0.3.4"
    const val VERSION_CODE = 160
    const val XRAY_CORE_VERSION = "v26.7.28"
    const val ANDROID_LIB_XRAY_LITE_VERSION = "v26.7.31"
    const val HEV_SOCKS5_TUNNEL_VERSION = "2.17.1"
}
