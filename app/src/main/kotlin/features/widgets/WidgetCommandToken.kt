// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Widget receivers are exported for the launcher, so any app could send their
 * custom actions. Intents SKIPI builds itself carry this per-install secret and
 * everything else is ignored.
 */
internal object WidgetCommandToken {
    const val Extra = "features.widgets.extra.COMMAND_TOKEN"
    private const val PreferencesName = "widget_command"
    private const val PreferencesKey = "token"

    fun matches(provided: String?, expected: String): Boolean {
        if (provided.isNullOrEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    fun generate(): String {
        val bytes = ByteArray(16).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun get(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        preferences.getString(PreferencesKey, null)?.takeIf(String::isNotEmpty)?.let { return it }
        return generate().also { token -> preferences.edit().putString(PreferencesKey, token).apply() }
    }
}
