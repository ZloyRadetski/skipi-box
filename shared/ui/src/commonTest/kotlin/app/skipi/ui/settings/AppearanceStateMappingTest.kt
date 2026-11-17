// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import app.CustomColors
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.HomeDisplaySettings
import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.withCustomColors
import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceStateMappingTest {
    private val colors = CustomColors(accent = 0xFF112233, protocolJson = 0xFF445566)

    private val persisted = PersistedSettings(
        appearance = AppearanceSettings(
            themeMode = ThemeMode.Named,
            themeVariant = "forest",
            dynamicColors = false,
            seedIndex = 3,
            customMaterialYouSeed = 77L,
            customColorsEnabled = true,
            fontFamilyMode = 2,
            fontSizeMode = 115,
            fontWeightMode = 1,
            backgroundStyle = 2,
            backgroundPhotoDimPercent = 60,
            bottomBarSize = 0,
            classicButtonSize = 3,
            seasonalEvents = false,
            customThemes = "a\nb",
        ).withCustomColors(colors),
        home = HomeDisplaySettings(
            connectionDisplayMode = 0,
            pinConnectionPanel = true,
            subscriptionSwipeEnabled = false,
            showFloatingPowerButton = true,
            showServerSearch = true,
            enableAllProxyGroup = false,
            showTunnelMemoryOnHome = true,
            showTunnelIpOnHome = false,
            maskTunnelIpOnHome = true,
        ),
    )

    @Test
    fun theStateCarriesEveryAppearanceAndHomeChoice() {
        val state = persisted.toAppearanceSettingsState(AppearanceHostSettings(appIcon = 2, navigationStyle = 1, showConnectionLogOnHome = false))

        assertEquals(AppearanceSettingValues.ColorModeForest, state.colorMode)
        assertEquals(false, state.enableMaterialYou)
        assertEquals(3, state.seedIndex)
        assertEquals(77L, state.customMaterialYouSeed)
        assertEquals(true, state.enableCustomColors)
        assertEquals(2, state.fontFamilyMode)
        assertEquals(115, state.fontSizeMode)
        assertEquals(1, state.fontWeightMode)
        assertEquals(2, state.backgroundStyle)
        assertEquals(60, state.backgroundPhotoDimPercent)
        assertEquals(0, state.bottomBarSize)
        assertEquals(3, state.classicButtonSize)
        assertEquals(false, state.enableSeasonalEvents)
        assertEquals("a\nb", state.customThemes)
        assertEquals(0, state.connectionDisplayMode)
        assertEquals(true, state.pinConnectionPanelOnHome)
        assertEquals(false, state.enableSubscriptionSwipe)
        assertEquals(true, state.classicShowFloatingPowerButton)
        assertEquals(true, state.showServerSearch)
        assertEquals(false, state.enableAllProxyGroup)
        assertEquals(true, state.showTunnelMemoryOnHome)
        assertEquals(false, state.showTunnelIpOnHome)
        assertEquals(true, state.maskTunnelIpOnHome)
        assertEquals(colors, state.customColors())
        // The three choices a client owns come from the host.
        assertEquals(2, state.appIcon)
        assertEquals(1, state.navigationStyle)
        assertEquals(false, state.showConnectionLogOnHome)
    }

    @Test
    fun valuesThePersistedSettingsLeaveOpenTakeTheScreensDefaults() {
        val state = PersistedSettings().toAppearanceSettingsState()
        val defaults = AppearanceSettingsState()

        assertEquals(defaults.fontFamilyMode, state.fontFamilyMode)
        assertEquals(app.modes.FontSizeModeSmall, state.fontSizeMode)
        assertEquals(defaults.backgroundStyle, state.backgroundStyle)
        assertEquals(defaults.backgroundPhotoDimPercent, state.backgroundPhotoDimPercent)
        assertEquals(defaults.bottomBarSize, state.bottomBarSize)
        assertEquals(defaults.classicButtonSize, state.classicButtonSize)
        assertEquals(defaults.connectionDisplayMode, state.connectionDisplayMode)
        assertEquals(defaults.enableAllProxyGroup, state.enableAllProxyGroup)
        assertEquals(defaults.showTunnelMemoryOnHome, state.showTunnelMemoryOnHome)
        assertEquals(defaults.showTunnelIpOnHome, state.showTunnelIpOnHome)
        assertEquals(defaults.maskTunnelIpOnHome, state.maskTunnelIpOnHome)
    }

    @Test
    fun aStateWrittenBackIntoThePersistedSettingsComesOutTheSame() {
        val state = persisted.toAppearanceSettingsState()

        assertEquals(state, persisted.withAppearanceSettingsState(state).toAppearanceSettingsState())
    }

    @Test
    fun writingAStateChangesOnlyTheAppearanceAndHomeSections() {
        val other = persisted.copy(proxy = app.skipi.app.model.ProxySelectionSettings(selectedServerId = 9))
        val changed = other.withAppearanceSettingsState(other.toAppearanceSettingsState().copy(enableSeasonalEvents = true, showServerSearch = false))

        assertEquals(other.proxy, changed.proxy)
        assertEquals(true, changed.appearance.seasonalEvents)
        assertEquals(false, changed.home.showServerSearch)
    }

    @Test
    fun theColorModeIsWrittenAsAThemeChoice() {
        val changed = persisted.withAppearanceSettingsState(
            persisted.toAppearanceSettingsState().copy(colorMode = AppearanceSettingValues.ColorModeAmoled),
        )

        assertEquals(ThemeMode.Dark, changed.appearance.themeMode)
        assertEquals(true, changed.appearance.amoledBlack)
    }

    @Test
    fun theHostSuppliesTheChoicesItOwnsWhenWriting() {
        val state = persisted.toAppearanceSettingsState(AppearanceHostSettings(appIcon = 5, navigationStyle = 1, showConnectionLogOnHome = false))

        assertEquals(AppearanceHostSettings(5, 1, false), state.hostSettings())
    }
}
