// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/** Context that hands out one [CountingSharedPreferences] for every preferences file. */
internal class FakePreferencesContext(
    val preferences: CountingSharedPreferences,
) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
}

/** In-memory preferences that remember how many times each key was written. */
internal class CountingSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any>()
    val writes = linkedMapOf<String, Int>()

    fun seedInt(key: String, value: Int) { values[key] = value }
    fun seedString(key: String, value: String) { values[key] = value }
    fun resetWriteCounts() = writes.clear()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (values[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    }
    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearRequested = false
        override fun putString(key: String?, value: String?) = set(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = set(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = set(key, value)
        override fun putLong(key: String?, value: Long) = set(key, value)
        override fun putFloat(key: String?, value: Float) = set(key, value)
        override fun putBoolean(key: String?, value: Boolean) = set(key, value)
        override fun remove(key: String?) = set(key, null)
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                writes[key] = (writes[key] ?: 0) + 1
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }
        override fun apply() {
            commit()
        }
        private fun set(key: String?, value: Any?): SharedPreferences.Editor {
            pending[requireNotNull(key)] = value
            return this
        }
    }
}
