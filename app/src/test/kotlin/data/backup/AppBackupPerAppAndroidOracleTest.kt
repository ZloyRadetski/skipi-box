// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.modes.ProxyAppListModeBlacklist
import features.config.SkipiPerAppMode
import features.config.SkipiPerAppModeGlobal
import features.config.SkipiPerAppModeWhitelist
import features.config.SkipiPerAppPackage
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.skipiSectionValues
import features.config.withSkipiSettingsInRawConfig
import features.subscription.DefaultSubscriptionUserAgent
import org.junit.Assert.assertEquals
import org.junit.Test

class AppBackupPerAppAndroidOracleTest {

    @Test
    fun appStateBackupRestoreKeepsProfilePolicySeparateAndWritesCanonicalProfileDocument() {
        val profileApps = listOf(
            "legacy.browser",
            "10:com.example.other-user",
            "987:com.example.unknown-package",
        )
        val targetProfile = canonicalProfile(
            id = 7,
            name = "Target",
            mode = SkipiPerAppModeWhitelist,
            selectedApps = profileApps,
        )
        val activeSibling = canonicalProfile(
            id = 3,
            name = "Active sibling",
            mode = SkipiPerAppModeGlobal,
            selectedApps = listOf("sibling.only"),
        )
        val globalApps = listOf("global.legacy", "0:com.example.global")
        val source = AppState(
            proxyAppListMode = ProxyAppListModeBlacklist,
            proxyAppListSelectedApps = globalApps,
            trafficConfigs = listOf(targetProfile, activeSibling),
            activeTrafficConfigId = activeSibling.id,
        )

        val backup = source.toAppBackupFile(
            createdAtMillis = 0L,
            appVersionName = "1.0",
            appVersionCode = 1,
        )
        val backedUpTarget = backup.data.trafficConfigs.single { config -> config.id == targetProfile.id }

        assertEquals(ProxyAppListModeBlacklist, backup.data.settings.proxyAppListMode)
        assertEquals(globalApps, backup.data.proxyAppListSelectedApps)
        assertEquals(SkipiPerAppModeWhitelist, backedUpTarget.proxyAppListMode)
        assertEquals(profileApps, backedUpTarget.proxyAppListSelectedApps)
        assertEquals(targetProfile.rawConfig, backedUpTarget.rawConfig)

        val restored = decodeAppBackup(encodeAppBackup(backup)).toRestorePreview().restoredState
        val restoredTarget = restored.trafficConfigs.single { config -> config.id == targetProfile.id }
        val restoredActive = restored.trafficConfigs.single { config -> config.id == activeSibling.id }

        assertEquals(ProxyAppListModeBlacklist, restored.proxyAppListMode)
        assertEquals(globalApps, restored.proxyAppListSelectedApps)
        assertEquals(SkipiPerAppModeWhitelist, restoredTarget.proxyAppListMode)
        assertEquals(profileApps, restoredTarget.proxyAppListSelectedApps)
        assertEquals(targetProfile.rawConfig, restoredTarget.rawConfig)
        assertEquals(listOf("whitelist"), restoredTarget.rawConfig.skipiSectionValues()[SkipiPerAppMode])
        assertEquals(profileApps, restoredTarget.rawConfig.skipiSectionValues()[SkipiPerAppPackage])
        assertEquals(SkipiPerAppModeGlobal, restoredActive.proxyAppListMode)
        assertEquals(listOf("sibling.only"), restoredActive.proxyAppListSelectedApps)
        assertEquals(activeSibling.id, restored.activeTrafficConfigId)
    }

    private fun canonicalProfile(
        id: Int,
        name: String,
        mode: Int,
        selectedApps: List<String>,
    ): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = "[General]\ndns-server = system",
        proxyAppListMode = mode,
        proxyAppListSelectedApps = selectedApps,
        resourceSettings = TrafficConfigResourceSettings(userAgent = DefaultSubscriptionUserAgent),
    ).withSkipiSettingsInRawConfig()
}
