// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.modes.BottomBarSizeMedium
import app.modes.ClassicButtonSizeGiant
import app.modes.FontSizeModeExtraLarge
import features.backup.AppBackupData
import features.backup.AppBackupFile
import features.backup.AppBackupFormat
import features.backup.AppBackupSettings
import features.backup.CurrentAppBackupVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every backup setting is given a value other than its default, restored, and exported again. A field that the
 * mapper drops, or copies into another field's place, no longer matches and fails here, whatever its name, so a
 * setting added to the backup is checked without anybody having to remember to extend a test.
 */
class EverySettingRoundTripTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Settings a restore checks: the value this test would invent is not one the app accepts, so it supplies one. */
    private val acceptedValues: Map<String, JsonElement> = mapOf(
        "fontSizeMode" to JsonPrimitive(FontSizeModeExtraLarge),
        "bottomBarSize" to JsonPrimitive(BottomBarSizeMedium),
        "classicButtonSize" to JsonPrimitive(ClassicButtonSizeGiant),
        "appUpdateChannel" to JsonPrimitive("nightly"),
        "localProxyAuthMode" to JsonPrimitive("Manual"),
        "localHttpProxyAuthMode" to JsonPrimitive("Manual"),
        "trafficStatsNotificationRefreshIntervalSeconds" to JsonPrimitive(7),
        "subscriptionUserAgents" to JsonArray(listOf(JsonPrimitive("agent/1"), JsonPrimitive("agent/2"))),
    )

    /** The only settings a restore deliberately replaces, whatever their value. */
    private val adjustedOnRestore = mapOf(
        "selectedProxyServerId" to "reset when the server is not in the backup",
        "hasCompletedOnboarding" to "a restored install never shows onboarding again",
        "customCategoryAppearanceColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategoryVpnColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategoryProxyColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategorySubscriptionsColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategoryIntegrationColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategoryLogsColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategoryBackupColor" to "legacy per-category colour, folded into customCategoryIconColor",
        "customCategoryAboutColor" to "legacy per-category colour, folded into customCategoryIconColor",
    )

    /**
     * A value that differs from the default and, for numbers, strings and colours, from every other field's
     * value, so a field copied into another one's place cannot go unnoticed. A flag has only two values, so
     * [flipBit] picks which flags are flipped: over all eight bits, any two fields get different values once.
     */
    private fun changed(index: Int, value: JsonElement, flipBit: Int): JsonElement = when {
        value is JsonNull -> JsonPrimitive(0xFF000000L + index + 1)
        value is JsonPrimitive && value.booleanOrNull != null ->
            JsonPrimitive(if ((index shr flipBit) and 1 == 1) !value.boolean else value.boolean)
        value is JsonPrimitive && value.isString -> JsonPrimitive(value.content + "#" + index)
        value is JsonPrimitive && value.longOrNull != null -> JsonPrimitive(value.longOrNull!! + index + 1)
        else -> value
    }

    @Test
    fun everySettingSurvivesARestoreExceptTheOnesNamedAbove() {
        val defaults = json.encodeToJsonElement(AppBackupSettings()).jsonObject
        val differing = mutableSetOf<String>()
        for (flipBit in 0 until 8) {
            val sent = JsonObject(
                defaults.entries.mapIndexed { index, (key, value) ->
                    key to (acceptedValues[key] ?: changed(index, value, flipBit))
                }.toMap(),
            )
            val settings = json.decodeFromJsonElement<AppBackupSettings>(sent)
            val file = AppBackupFile(
                format = AppBackupFormat,
                version = CurrentAppBackupVersion,
                data = AppBackupData(settings = settings),
            )

            val restored = file.toRestorePreview().restoredState
            val exported = json.encodeToJsonElement(
                restored.toAppBackupFile(createdAtMillis = 0L, appVersionName = "1", appVersionCode = 1).data.settings,
            ).jsonObject

            differing += sent.keys.filter { key -> sent[key] != exported[key] }
        }

        assertEquals(
            adjustedOnRestore.keys,
            differing,
            "settings that did not survive a restore: ${(differing - adjustedOnRestore.keys).sorted()}; " +
                "named as adjusted but surviving: ${(adjustedOnRestore.keys - differing).sorted()}",
        )
    }
}
