// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

/**
 * The package a Desktop build was installed from, and so the release asset that replaces it. The names
 * are the ones the release workflow uploads; the app never guesses from a pattern.
 */
enum class DesktopUpdatePackage(val assetName: String) {
    WindowsMsi("SKIPI-windows-x64.msi"),
    WindowsPortable("SKIPI-windows-x64-portable.zip"),
    LinuxAppImage("SKIPI-linux-x64.AppImage"),
    LinuxDeb("SKIPI-linux-x64.deb"),
    LinuxRpm("SKIPI-linux-x64.rpm"),
    LinuxPortable("SKIPI-linux-x64-portable.tar.gz"),
    MacDmg("SKIPI-macos-arm64.dmg"),
}
