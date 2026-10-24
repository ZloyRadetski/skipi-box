// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.backup

import features.config.SkipiPerAppModeBlacklist
import features.config.SkipiPerAppModeGlobal
import features.config.SkipiPerAppModeWhitelist
import features.config.TrafficConfigState
import kotlin.test.Test
import kotlin.test.assertEquals

class PerAppBackupRoundTripTest {
    @Test
    fun backupCopiesExactProfileIdsSeparatelyFromGlobalSelectionAndActiveProfile() {
        val profileIds = listOf(
            " profile.legacy ",
            "",
            "10:com.example.unknown-user",
            "profile.legacy",
        )
        val targetProfile = TrafficConfigState(
            id = 7,
            name = "Target profile",
            rawConfig = "[General]\ndns-server = system",
            proxyAppListMode = SkipiPerAppModeWhitelist,
            proxyAppListSelectedApps = profileIds,
        )
        val activeSibling = TrafficConfigState(
            id = 3,
            name = "Active sibling",
            rawConfig = "[General]\ndns-server = system",
            proxyAppListMode = SkipiPerAppModeGlobal,
            proxyAppListSelectedApps = listOf("sibling.only"),
        )
        val globalIds = listOf("global.legacy", "0:com.example.global")
        val backup = AppBackupFile(
            format = AppBackupFormat,
            version = CurrentAppBackupVersion,
            data = AppBackupData(
                settings = AppBackupSettings(proxyAppListMode = SkipiPerAppModeBlacklist),
                proxyAppListSelectedApps = globalIds,
                trafficConfigs = listOf(
                    targetProfile.toAppBackupTrafficConfig(),
                    activeSibling.toAppBackupTrafficConfig(),
                ),
                activeTrafficConfigId = activeSibling.id,
            ),
        )

        val targetDto = targetProfile.toAppBackupTrafficConfig()
        assertEquals(targetProfile, targetDto.toTrafficConfigState())

        val restored = decodeAppBackup(encodeAppBackup(backup)).data
        val restoredTarget = restored.trafficConfigs.single { it.id == targetProfile.id }
        val restoredActive = restored.trafficConfigs.single { it.id == activeSibling.id }

        assertEquals(SkipiPerAppModeBlacklist, restored.settings.proxyAppListMode)
        assertEquals(globalIds, restored.proxyAppListSelectedApps)
        assertEquals(SkipiPerAppModeWhitelist, restoredTarget.proxyAppListMode)
        assertEquals(profileIds, restoredTarget.proxyAppListSelectedApps)
        assertEquals(targetProfile, restoredTarget.toTrafficConfigState())
        assertEquals(SkipiPerAppModeGlobal, restoredActive.proxyAppListMode)
        assertEquals(listOf("sibling.only"), restoredActive.proxyAppListSelectedApps)
        assertEquals(activeSibling.id, restored.activeTrafficConfigId)
    }
}
