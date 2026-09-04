// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import app.navigation.Route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class SettingsRoutesTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun testSettingsGeneralSerialization() {
        val route: Route = Route.SettingsGeneral
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<Route>(encoded)
        assertEquals(route, decoded)
    }

    @Test
    fun testSettingsAppearanceSerialization() {
        val route: Route = Route.SettingsAppearance
        val encoded = json.encodeToString(route)
        val decoded = json.decodeFromString<Route>(encoded)
        assertEquals(route, decoded)
    }
}
