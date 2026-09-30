// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import app.AppState
import app.modes.ConnectionDisplayModeCompact
import app.modes.ProxyServerListLayoutDouble
import app.modes.ProxyServerListLayoutMultiple
import features.config.TrafficConfigState
import features.subscription.DefaultSubscriptionUserAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsPreferencesCompatibilityTest {
    @Test
    fun loadsLiteralLegacyHomeKeysAndOlderTrafficProfileJson() {
        val preferences = InMemorySharedPreferences().apply {
            seedInt("selected_proxy_server_id", 52)
            seedInt("next_proxy_server_id", 2)
            seedInt("proxy_server_list_layout", 3)
            seedInt("proxy_server_list_sort", 4)
            seedInt("active_traffic_config_id", 999)
            seedInt("next_traffic_config_id", 2)
            seedLong("custom_category_appearance_color", 0x11223344L)
            seedString("subscription_user_agents", "[\"Legacy agent\",\"Custom agent\"]")
            seedString("traffic_configs", """
                [
                  {
                    "id": 9,
                    "name": "Legacy profile",
                    "rawConfig": "[General]\ndns-server = system",
                    "sourceUrl": "https://example.com/profile",
                    "updateLocked": false,
                    "lastUpdatedAtMillis": 17,
                    "proxyAppListMode": 0,
                    "proxyAppListSelectedApps": ["com.example.browser"],
                    "futureTrafficConfigField": true
                  }
                ]
                """.trimIndent())
        }

        val state = AppSettingsPreferences(TestContext(preferences)).load()

        assertEquals(52, state.selectedProxyServerId)
        assertEquals(2, state.nextProxyServerId)
        assertEquals(3, state.proxyServerListLayout)
        assertEquals(4, state.proxyServerListSort)
        assertEquals(0x11223344L, state.customCategoryIconColor)
        assertEquals(
            listOf(DefaultSubscriptionUserAgent, "Legacy agent", "Custom agent"),
            state.subscriptionUserAgents,
        )
        assertEquals(9, state.trafficConfigs.single().id)
        assertEquals("Legacy profile", state.trafficConfigs.single().name)
        assertFalse(state.trafficConfigs.single().autoUpdate)
        assertEquals("", state.trafficConfigs.single().updateInterval)
        assertEquals(listOf("com.example.browser"), state.trafficConfigs.single().proxyAppListSelectedApps)
        assertEquals(10, state.nextTrafficConfigId)
        assertEquals(9, state.activeTrafficConfigId)
    }

    @Test
    fun savesAndReloadsTheExistingPreferenceKeysAndJsonValues() {
        val preferences = InMemorySharedPreferences()
        val context = TestContext(preferences)
        val store = AppSettingsPreferences(context)
        val state = AppState(
            selectedProxyServerId = 73,
            nextProxyServerId = 74,
            proxyServerListLayout = 2,
            proxyServerListSort = 1,
            customCategoryIconColor = 0x55667788L,
            subscriptionUserAgents = listOf("First", "Second"),
            trafficConfigs = listOf(
                TrafficConfigState(
                    id = 8,
                    name = "Stored profile",
                    rawConfig = "[General]",
                    autoUpdate = true,
                    updateInterval = "12",
                    proxyAppListSelectedApps = listOf("com.example.mail"),
                ),
            ),
            nextTrafficConfigId = 9,
            activeTrafficConfigId = 8,
        )

        store.save(state)

        assertEquals("skipi_settings", context.openedName)
        assertEquals(73, preferences.getInt("selected_proxy_server_id", -1))
        assertEquals(74, preferences.getInt("next_proxy_server_id", -1))
        assertEquals(0x55667788L, preferences.getLong("custom_category_icon_color", -1L))
        assertTrue(preferences.getString("traffic_configs", "").orEmpty().contains("\"updateInterval\":\"12\""))

        val restored = store.load()
        assertEquals(73, restored.selectedProxyServerId)
        assertEquals(74, restored.nextProxyServerId)
        assertEquals(2, restored.proxyServerListLayout)
        assertEquals(1, restored.proxyServerListSort)
        assertEquals(0x55667788L, restored.customCategoryIconColor)
        assertEquals(
            listOf(DefaultSubscriptionUserAgent, "First", "Second"),
            restored.subscriptionUserAgents,
        )
        assertEquals(state.trafficConfigs, restored.trafficConfigs)
        assertEquals(9, restored.nextTrafficConfigId)
        assertEquals(8, restored.activeTrafficConfigId)
    }

    @Test
    fun loadsLiteralLegacyHomeKeysWithNonDefaultValues() {
        val preferences = InMemorySharedPreferences().apply {
            seedInt("connection_display_mode", ConnectionDisplayModeCompact)
            seedBoolean("pin_connection_panel_on_home", true)
            seedBoolean("classic_show_floating_power_button", true)
            seedInt("proxy_server_list_layout", ProxyServerListLayoutMultiple)
            seedBoolean("enable_subscription_swipe", false)
            seedBoolean("show_server_search", true)
            seedBoolean("enable_all_proxy_group", true)
            seedBoolean("show_tunnel_memory_on_home", true)
        }

        val state = AppSettingsPreferences(TestContext(preferences)).load()

        assertEquals(ConnectionDisplayModeCompact, state.connectionDisplayMode)
        assertTrue(state.pinConnectionPanelOnHome)
        assertTrue(state.classicShowFloatingPowerButton)
        assertEquals(ProxyServerListLayoutMultiple, state.proxyServerListLayout)
        assertFalse(state.enableSubscriptionSwipe)
        assertTrue(state.showServerSearch)
        assertTrue(state.enableAllProxyGroup)
        assertTrue(state.showTunnelMemoryOnHome)
    }

    @Test
    fun savesAndReloadsLegacyHomeSettingsAcrossTwoAndThreeColumnLayouts() {
        val preferences = InMemorySharedPreferences()
        val context = TestContext(preferences)
        val store = AppSettingsPreferences(context)

        val nonDefaultStateTwoColumns = AppState(
            connectionDisplayMode = ConnectionDisplayModeCompact,
            pinConnectionPanelOnHome = true,
            classicShowFloatingPowerButton = true,
            proxyServerListLayout = ProxyServerListLayoutDouble,
            enableSubscriptionSwipe = false,
            showServerSearch = true,
            enableAllProxyGroup = true,
            showTunnelMemoryOnHome = true,
        )

        store.save(nonDefaultStateTwoColumns)

        assertEquals(ConnectionDisplayModeCompact, preferences.getInt("connection_display_mode", -1))
        assertTrue(preferences.getBoolean("pin_connection_panel_on_home", false))
        assertTrue(preferences.getBoolean("classic_show_floating_power_button", false))
        assertEquals(ProxyServerListLayoutDouble, preferences.getInt("proxy_server_list_layout", -1))
        assertFalse(preferences.getBoolean("enable_subscription_swipe", true))
        assertTrue(preferences.getBoolean("show_server_search", false))
        assertTrue(preferences.getBoolean("enable_all_proxy_group", false))
        assertTrue(preferences.getBoolean("show_tunnel_memory_on_home", false))

        val restoredTwoColumns = store.load()
        assertEquals(ConnectionDisplayModeCompact, restoredTwoColumns.connectionDisplayMode)
        assertTrue(restoredTwoColumns.pinConnectionPanelOnHome)
        assertTrue(restoredTwoColumns.classicShowFloatingPowerButton)
        assertEquals(ProxyServerListLayoutDouble, restoredTwoColumns.proxyServerListLayout)
        assertFalse(restoredTwoColumns.enableSubscriptionSwipe)
        assertTrue(restoredTwoColumns.showServerSearch)
        assertTrue(restoredTwoColumns.enableAllProxyGroup)
        assertTrue(restoredTwoColumns.showTunnelMemoryOnHome)

        val nonDefaultStateThreeColumns = restoredTwoColumns.copy(
            proxyServerListLayout = ProxyServerListLayoutMultiple,
        )

        store.save(nonDefaultStateThreeColumns)

        assertEquals(ProxyServerListLayoutMultiple, preferences.getInt("proxy_server_list_layout", -1))

        val restoredThreeColumns = store.load()
        assertEquals(ProxyServerListLayoutMultiple, restoredThreeColumns.proxyServerListLayout)
        assertEquals(ConnectionDisplayModeCompact, restoredThreeColumns.connectionDisplayMode)
        assertTrue(restoredThreeColumns.pinConnectionPanelOnHome)
        assertTrue(restoredThreeColumns.classicShowFloatingPowerButton)
        assertFalse(restoredThreeColumns.enableSubscriptionSwipe)
        assertTrue(restoredThreeColumns.showServerSearch)
        assertTrue(restoredThreeColumns.enableAllProxyGroup)
        assertTrue(restoredThreeColumns.showTunnelMemoryOnHome)
    }
}

private class TestContext(
    private val preferences: SharedPreferences,
) : ContextWrapper(null) {
    var openedName: String? = null
        private set

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        openedName = name
        return preferences
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any>()

    fun seedInt(key: String, value: Int) { values[key] = value }
    fun seedLong(key: String, value: Long) { values[key] = value }
    fun seedString(key: String, value: String) { values[key] = value }
    fun seedBoolean(key: String, value: Boolean) { values[key] = value }

    override fun getAll(): MutableMap<String, *> = synchronized(values) { values.toMutableMap() }
    override fun getString(key: String?, defValue: String?): String? = synchronized(values) {
        (values[key] as? String) ?: defValue
    }
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(values) {
        @Suppress("UNCHECKED_CAST")
        ((values[key] as? Set<String>)?.toMutableSet()) ?: defValues?.toMutableSet()
    }
    override fun getInt(key: String?, defValue: Int): Int = synchronized(values) {
        (values[key] as? Int) ?: defValue
    }
    override fun getLong(key: String?, defValue: Long): Long = synchronized(values) {
        (values[key] as? Long) ?: defValue
    }
    override fun getFloat(key: String?, defValue: Float): Float = synchronized(values) {
        (values[key] as? Float) ?: defValue
    }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(values) {
        (values[key] as? Boolean) ?: defValue
    }
    override fun contains(key: String?): Boolean = synchronized(values) { values.containsKey(key) }
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val writes = linkedMapOf<String, Any>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = set(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            set(key, values?.toMutableSet())
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = set(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = set(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = set(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = set(key, value)
        override fun remove(key: String?): SharedPreferences.Editor {
            val requiredKey = requireNotNull(key)
            writes.remove(requiredKey)
            removals += requiredKey
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            synchronized(values) {
                if (clearRequested) values.clear()
                removals.forEach(values::remove)
                values.putAll(writes)
            }
            return true
        }
        override fun apply() { commit() }

        private fun set(key: String?, value: Any?): SharedPreferences.Editor {
            val requiredKey = requireNotNull(key)
            if (value == null) {
                writes.remove(requiredKey)
                removals += requiredKey
            } else {
                removals -= requiredKey
                writes[requiredKey] = value
            }
            return this
        }
    }
}
