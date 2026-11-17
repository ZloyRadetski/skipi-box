// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.CustomColors
import app.skipi.ui.settings.AppearanceHostSettings
import app.skipi.ui.settings.AppearanceSettingValues
import app.skipi.ui.settings.AppearanceSettingsState
import app.skipi.ui.settings.hostSettings
import app.skipi.ui.settings.toAppearanceSettingsState
import app.skipi.ui.settings.withCustomColors
import app.skipi.ui.settings.withAppearanceSettingsState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Temporary oracle of the move to the shared appearance mapper: for many Desktop settings the shared path
 * (PersistedSettings, one mapper) must give exactly what Desktop's own mappers give. It goes when Desktop's
 * mappers do.
 */
class DesktopAppearanceStateOracleTest {
    private val colors = CustomColors(accent = 0xFF010203, background = 0xFF040506, pingSlow = 0xFF070809, protocolChain = 0xFF0A0B0C)

    private fun samples(): List<DesktopAppSettings> {
        val bases = DesktopThemeMode.entries.map { DesktopAppSettings(themeMode = it) }
        val variations = listOf<(DesktopAppSettings) -> DesktopAppSettings>(
            { it },
            { it.copy(compactHome = true) },
            { it.copy(compactHome = true, connectionDisplayMode = 1) },
            { it.copy(connectionDisplayMode = 2) },
            { it.copy(connectionDisplayMode = 0, compactHome = false) },
            { it.copy(fontFamilyMode = 3, fontSizeMode = 120, fontWeightMode = 1, backgroundStyle = 2, backgroundPhotoDimPercent = 70, bottomBarSize = 0, classicButtonSize = 3) },
            {
                it.copy(
                    dynamicColors = false, customColorsEnabled = true, seedIndex = 4, customMaterialYouSeed = 99L,
                    enableSeasonalEvents = false, customThemes = "x\ny", classicShowFloatingPowerButton = true,
                    pinConnectionPanelOnHome = false, enableAllProxyGroup = false, showServerSearch = true,
                    enableSubscriptionSwipe = false, showTunnelMemory = true, showTunnelIp = false, maskTunnelIp = true,
                    showConnectionLog = false, navigationStyle = 1,
                ).withCustomColors(colors)
            },
        )
        return bases.flatMap { base -> variations.map { it(base) } }
    }

    private class Fixture(settings: DesktopAppSettings) {
        var settings: DesktopAppSettings = settings
        private var servers = DesktopServerLibrary()
        private var configs = DesktopConfigLibrary()
        val repository = DesktopSettingsRepository(
            readSettings = { this.settings },
            saveSettings = { this.settings = it; Result.success(Unit) },
            publishSettings = { this.settings = it },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )
    }

    private fun DesktopAppSettings.host() = AppearanceHostSettings(appIcon = 0, navigationStyle = navigationStyle, showConnectionLogOnHome = showConnectionLog)

    @Test
    fun theSharedMapperGivesTheStateDesktopGaveBefore() = runBlocking {
        for (settings in samples()) {
            val shared = Fixture(settings).repository.state.value.toAppearanceSettingsState(settings.host())

            assertEquals(settings.toAppearanceSettingsState(), shared, "settings $settings")
        }
    }

    @Test
    fun writingThroughTheSharedMapperGivesTheSettingsDesktopWroteBefore() = runBlocking {
        val edits = listOf<(AppearanceSettingsState) -> AppearanceSettingsState>(
            { it },
            { it.copy(colorMode = AppearanceSettingValues.ColorModeAmoled) },
            { it.copy(colorMode = AppearanceSettingValues.ColorModeSunset, enableSeasonalEvents = false) },
            { it.copy(connectionDisplayMode = AppearanceSettingValues.ConnectionDisplayModeCompact, showServerSearch = true) },
            { it.copy(connectionDisplayMode = AppearanceSettingValues.ConnectionDisplayModeLegacy, fontSizeMode = 90, seedIndex = 2) },
            { it.copy(enableCustomColors = true, navigationStyle = 1, showConnectionLogOnHome = false).withCustomColors(colors) },
        )
        for (settings in samples()) {
            for (edit in edits) {
                val state = edit(settings.toAppearanceSettingsState())
                val fixture = Fixture(settings)

                fixture.repository.update { it.withAppearanceSettingsState(state) }
                val viaShared = fixture.settings.copy(
                    navigationStyle = state.hostSettings().navigationStyle,
                    showConnectionLog = state.hostSettings().showConnectionLogOnHome,
                )

                assertEquals(settings.withAppearanceState(state), viaShared, "settings $settings edit $state")
            }
        }
    }
}
