// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.AppState
import app.CustomColors
import app.skipi.ui.settings.AppearanceHostSettings
import app.skipi.ui.settings.AppearanceSettingValues
import app.skipi.ui.settings.AppearanceSettingsState
import app.skipi.ui.settings.hostSettings
import app.skipi.ui.settings.toAppearanceSettingsState
import app.skipi.ui.settings.withAppearanceSettingsState
import app.skipi.ui.settings.withCustomColors
import app.withCustomColors
import data.repository.applyPersistedSettings
import data.repository.toPersistedSettings
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Temporary oracle of the move to the shared appearance mapper: through PersistedSettings, with one mapper, Android
 * must get exactly what its own two mappers give. It goes when those mappers do.
 */
class AndroidAppearanceStateOracleTest {
    private val colors = CustomColors(accent = 0xFF010203, text = 0xFF040506, pingFast = 0xFF070809, protocolHttp = 0xFF0A0B0C)

    private fun samples(): List<AppState> {
        val modes = listOf(
            app.modes.ColorModeSystem, app.modes.ColorModeLight, app.modes.ColorModeDark, app.modes.ColorModeAmoled,
            app.modes.ColorModeAurora, app.modes.ColorModeSakura, app.modes.ColorModeForest, app.modes.ColorModeSunset,
        )
        val variations = listOf<(AppState) -> AppState>(
            { it },
            { it.copy(connectionDisplayMode = 0, bottomBarSize = 0, classicButtonSize = 3, appIcon = 2) },
            { it.copy(connectionDisplayMode = 2, fontFamilyMode = 3, fontSizeMode = 120, fontWeightMode = 1, backgroundStyle = 2) },
            {
                it.copy(
                    enableMaterialYou = false, enableCustomColors = true, seedIndex = 4, customMaterialYouSeed = 99L,
                    enableSeasonalEvents = false, customThemes = "x\ny", classicShowFloatingPowerButton = false,
                    pinConnectionPanelOnHome = false, enableAllProxyGroup = false, showServerSearch = true,
                    enableSubscriptionSwipe = false, showTunnelMemoryOnHome = true, showTunnelIpOnHome = false,
                    maskTunnelIpOnHome = true, backgroundPhotoDimPercent = 70, appIcon = 1,
                ).withCustomColors(colors)
            },
        )
        return modes.flatMap { mode -> variations.map { it(AppState(colorMode = mode)) } }
    }

    @Test
    fun theSharedMapperGivesTheStateAndroidGaveBefore() {
        for (state in samples()) {
            val shared = state.toPersistedSettings().toAppearanceSettingsState(AppearanceHostSettings(appIcon = state.appIcon))

            assertEquals(state.toAppearanceSettingsState(), shared, "state $state")
        }
    }

    @Test
    fun writingThroughTheSharedMapperGivesTheStateAndroidWroteBefore() {
        val edits = listOf<(AppearanceSettingsState) -> AppearanceSettingsState>(
            { it },
            { it.copy(colorMode = AppearanceSettingValues.ColorModeAmoled) },
            { it.copy(colorMode = AppearanceSettingValues.ColorModeSunset, enableSeasonalEvents = false, appIcon = 3) },
            { it.copy(connectionDisplayMode = AppearanceSettingValues.ConnectionDisplayModeCompact, showServerSearch = true) },
            { it.copy(connectionDisplayMode = AppearanceSettingValues.ConnectionDisplayModeLegacy, fontSizeMode = 90, seedIndex = 2) },
            { it.copy(enableCustomColors = true).withCustomColors(colors) },
        )
        for (state in samples()) {
            for (edit in edits) {
                val screen = edit(state.toAppearanceSettingsState())

                val viaShared = state
                    .applyPersistedSettings(state.toPersistedSettings().withAppearanceSettingsState(screen))
                    .copy(appIcon = screen.hostSettings().appIcon)

                assertEquals(state.withAppearanceSettings(screen), viaShared, "state $state edit $screen")
            }
        }
    }
}
