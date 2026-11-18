// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.AppState
import app.CustomColors
import app.customColors
import app.withCustomColors
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.customColors
import app.skipi.app.model.withCustomColors
import app.skipi.ui.settings.AppearanceSettingsState
import app.skipi.ui.settings.customColors
import app.skipi.ui.settings.withCustomColors
import features.backup.AppBackupSettings
import features.backup.customColors
import features.backup.withCustomColors
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every settings model stores the same 24 colors flat; a color one model forgets is lost on the way through. */
class CustomColorsConversionTest {
    // Every color a different value, so a swapped or dropped field shows up.
    private val colors = CustomColors(
        accent = 0xFF000001,
        background = 0xFF000002,
        surface = 0xFF000003,
        surfaceVariant = 0xFF000004,
        text = 0xFF000005,
        textSecondary = 0xFF000006,
        statusRunning = 0xFF000007,
        statusStopped = 0xFF000008,
        pingFast = 0xFF000009,
        pingMedium = 0xFF00000A,
        pingSlow = 0xFF00000B,
        categoryIcon = 0xFF00000C,
        categoryIconGlyph = 0xFF00000D,
        protocolVless = 0xFF00000E,
        protocolVmess = 0xFF00000F,
        protocolHysteria2 = 0xFF000010,
        protocolTrojan = 0xFF000011,
        protocolShadowsocks = 0xFF000012,
        protocolWireguard = 0xFF000013,
        protocolSocks = 0xFF000014,
        protocolHttp = 0xFF000015,
        protocolStrategy = 0xFF000016,
        protocolChain = 0xFF000017,
        protocolJson = 0xFF000018,
        protocolMasque = 0xFF000019,
    )

    @Test
    fun noColorsIsTheDefault() {
        assertEquals(CustomColors(), AppState().customColors())
    }

    @Test
    fun theAppStateKeepsEveryColor() {
        assertEquals(colors, AppState().withCustomColors(colors).customColors())
    }

    @Test
    fun theBackupKeepsEveryColor() {
        assertEquals(colors, AppBackupSettings().withCustomColors(colors).customColors())
    }

    @Test
    fun thePersistedRecordKeepsEveryColor() {
        assertEquals(colors, AppearanceSettings().withCustomColors(colors).customColors())
    }

    @Test
    fun theSettingsScreenStateKeepsEveryColor() {
        assertEquals(colors, AppearanceSettingsState().withCustomColors(colors).customColors())
    }

    @Test
    fun theDesktopSettingsKeepEveryColor() {
        assertEquals(colors, DesktopAppSettings().withCustomColors(colors).customColors())
    }

    @Test
    fun colorsSurviveTheWholeChainOfModels() {
        val throughAll = DesktopAppSettings().withCustomColors(
            AppearanceSettingsState().withCustomColors(
                AppearanceSettings().withCustomColors(
                    AppBackupSettings().withCustomColors(
                        AppState().withCustomColors(colors).customColors(),
                    ).customColors(),
                ).customColors(),
            ).customColors(),
        ).customColors()

        assertEquals(colors, throughAll)
    }

    @Test
    fun convertingColorsLeavesTheOtherSettingsAlone() {
        val state = AppState(showServerSearch = true, seedIndex = 7)
        val changed = state.withCustomColors(colors)

        assertEquals(state.showServerSearch, changed.showServerSearch)
        assertEquals(state.seedIndex, changed.seedIndex)
    }
}
