// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsScreenModelsTest {
    @Test
    fun aboutRuntimeInfoKeepsHostProvidedVersionsVerbatim() {
        val runtime = AboutRuntimeInfo("SKIPI", "1.2 (34)", "core-dev", "xray", "hev")

        assertEquals("1.2 (34)", runtime.appVersion)
        assertEquals("core-dev", runtime.skipiCoreVersion)
    }

    @Test
    fun backupResetLabelsKeepDistinctActions() {
        val labels = BackupResetSettingsLabels(
            "Backup", "Backup and restore", "Export", "Export data", "Restore", "Restore data",
            "Reset", "Stop tunnel", "Stop only", "Reset VPN", "Reset VPN settings", "Reset app", "Clear app data",
        )

        assertEquals("Stop tunnel", labels.resetTunnelTitle)
        assertEquals("Reset app", labels.resetAppTitle)
    }
}
