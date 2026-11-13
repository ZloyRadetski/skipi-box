// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsPreferencesIncrementalTest {
    private fun newPreferences(): Pair<CountingSharedPreferences, AppSettingsPreferences> {
        val prefs = CountingSharedPreferences()
        return prefs to AppSettingsPreferences(FakePreferencesContext(prefs))
    }

    @Test
    fun loadLocaleAndColorMode_matchesFullLoad() {
        val (_, settings) = newPreferences()
        settings.save(AppState().copy(languageMode = 2, colorMode = 1))

        val light = settings.loadLocaleAndColorMode()
        val full = settings.load()

        assertEquals(full.languageMode, light.languageMode)
        assertEquals(full.colorMode, light.colorMode)
    }

    @Test
    fun loadLocaleAndColorMode_onEmptyPreferencesUsesDefaults() {
        val (_, settings) = newPreferences()
        val light = settings.loadLocaleAndColorMode()
        val defaults = settings.load()
        assertEquals(defaults.languageMode, light.languageMode)
        assertEquals(defaults.colorMode, light.colorMode)
    }

    @Test
    fun changingOneBoolean_doesNotRewriteBigBlobs() {
        val (prefs, settings) = newPreferences()
        val first = AppState()
        settings.save(first)
        prefs.resetWriteCounts()

        val second = first.copy(enableHaptics = !first.enableHaptics)
        settings.save(second, previous = first)

        assertFalse(prefs.writes.containsKey("traffic_configs"))
        assertFalse(prefs.writes.containsKey("custom_themes"))
        assertEquals(second.enableHaptics, settings.load().enableHaptics)
    }

    @Test
    fun changingTrafficConfigs_rewritesThem() {
        val (prefs, settings) = newPreferences()
        val first = AppState()
        settings.save(first)
        prefs.resetWriteCounts()

        val changed = first.copy(
            trafficConfigs = first.trafficConfigs.map { it.copy(name = it.name + "!") },
        )
        settings.save(changed, previous = first)

        assertEquals(1, prefs.writes["traffic_configs"])
        assertEquals(changed.trafficConfigs, settings.load().trafficConfigs)
    }

    @Test
    fun saveWithoutPrevious_writesEverything() {
        val (prefs, settings) = newPreferences()
        settings.save(AppState())
        assertEquals(1, prefs.writes["traffic_configs"])
    }
}
