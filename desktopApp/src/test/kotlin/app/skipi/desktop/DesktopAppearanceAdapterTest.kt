// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ThemeMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAppearanceAdapterTest {
    @Test
    fun desktopAppearanceSettingsExposePortableCustomColorsAndFonts() {
        val capabilities = desktopAppearanceSettingsCapabilities()

        assertTrue(capabilities.customColors)
        assertTrue(capabilities.fonts)
    }

    @Test
    fun allCustomColorsFontsAndAppearanceFieldsRoundTripThroughRepository(): Unit = runBlocking {
        val fixture = Fixture()
        val values = listOf(0xFF000001L, 0xFF000002L, 0xFF000003L, 0xFF000004L, 0xFF000005L,
            0xFF000006L, 0xFF000007L, 0xFF000008L, 0xFF000009L, 0xFF00000AL, 0xFF00000BL,
            0xFF00000CL, 0xFF00000DL, 0xFF00000EL, 0xFF00000FL, 0xFF000010L, 0xFF000011L,
            0xFF000012L, 0xFF000013L, 0xFF000014L, 0xFF000015L, 0xFF000016L, 0xFF000017L)
        val expectedAppearance = fixture.repository.state.value.appearance.copy(
            themeMode = ThemeMode.Named,
            themeVariant = "sakura",
            customMaterialYouSeed = 0xFF123456L,
            customColorsEnabled = true,
            customAccentColor = values[0],
            customBackgroundColor = values[1],
            customSurfaceColor = values[2],
            customSurfaceVariantColor = values[3],
            customTextColor = values[4],
            customTextSecondaryColor = values[5],
            customStatusRunningColor = values[6],
            customStatusStoppedColor = values[7],
            customPingFastColor = values[8],
            customPingMediumColor = values[9],
            customPingSlowColor = values[10],
            customCategoryIconColor = values[11],
            customProtocolVlessColor = values[12],
            customProtocolVmessColor = values[13],
            customProtocolHysteria2Color = values[14],
            customProtocolTrojanColor = values[15],
            customProtocolShadowsocksColor = values[16],
            customProtocolWireguardColor = values[17],
            customProtocolSocksColor = values[18],
            customProtocolHttpColor = values[19],
            customProtocolStrategyColor = values[20],
            customProtocolChainColor = values[21],
            customProtocolJsonColor = values[22],
            fontFamilyMode = 4,
            fontSizeMode = 115,
            fontWeightMode = 5,
            backgroundStyle = 1,
            backgroundPhotoDimPercent = 71,
            bottomBarSize = 2,
        )

        fixture.repository.update { current -> current.copy(appearance = expectedAppearance) }

        val settings = fixture.settings
        assertEquals(values[0], settings.customAccentColor)
        assertEquals(values[1], settings.customBackgroundColor)
        assertEquals(values[2], settings.customSurfaceColor)
        assertEquals(values[3], settings.customSurfaceVariantColor)
        assertEquals(values[4], settings.customTextColor)
        assertEquals(values[5], settings.customTextSecondaryColor)
        assertEquals(values[6], settings.customStatusRunningColor)
        assertEquals(values[7], settings.customStatusStoppedColor)
        assertEquals(values[8], settings.customPingFastColor)
        assertEquals(values[9], settings.customPingMediumColor)
        assertEquals(values[10], settings.customPingSlowColor)
        assertEquals(values[11], settings.customCategoryIconColor)
        assertEquals(values[12], settings.customProtocolVlessColor)
        assertEquals(values[13], settings.customProtocolVmessColor)
        assertEquals(values[14], settings.customProtocolHysteria2Color)
        assertEquals(values[15], settings.customProtocolTrojanColor)
        assertEquals(values[16], settings.customProtocolShadowsocksColor)
        assertEquals(values[17], settings.customProtocolWireguardColor)
        assertEquals(values[18], settings.customProtocolSocksColor)
        assertEquals(values[19], settings.customProtocolHttpColor)
        assertEquals(values[20], settings.customProtocolStrategyColor)
        assertEquals(values[21], settings.customProtocolChainColor)
        assertEquals(values[22], settings.customProtocolJsonColor)
        assertEquals(0xFF123456L, settings.customMaterialYouSeed)
        assertEquals(4, settings.fontFamilyMode)
        assertEquals(115, settings.fontSizeMode)
        assertEquals(5, settings.fontWeightMode)
        assertEquals(1, settings.backgroundStyle)
        assertEquals(71, settings.backgroundPhotoDimPercent)
        assertEquals(2, settings.bottomBarSize)
        assertEquals(expectedAppearance.customProtocolJsonColor, fixture.repository.state.value.appearance.customProtocolJsonColor)
    }

    private class Fixture {
        var settings = DesktopAppSettings(themeMode = DesktopThemeMode.Sakura)
        private var servers = DesktopServerLibrary()
        private var configs = DesktopConfigLibrary()

        val repository = DesktopSettingsRepository(
            readSettings = { settings },
            saveSettings = { settings = it; Result.success(Unit) },
            publishSettings = { settings = it },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )
    }
}
