// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import kotlinx.serialization.json.Json
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
 * Every Desktop setting is given a value other than its default, put through a backup and restored. What does not
 * come back is named below with the reason, so that a setting forgotten by the backup shows up here as a failure
 * instead of as a surprise on the day somebody restores.
 */
class DesktopEverySettingBackupTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Values the restore checks or the model requires, so the test supplies ones that are valid. */
    private val acceptedValues: Map<String, JsonElement> = mapOf(
        "appUpdateChannelId" to JsonPrimitive("nightly"),
        "bottomBarSize" to JsonPrimitive(0),
        "classicButtonSize" to JsonPrimitive(3),
        "coreLogLevel" to JsonPrimitive("warning"),
        "localProxyListenAddress" to JsonPrimitive("0.0.0.0"),
        "localProxyPort" to JsonPrimitive(12_345),
        "localProxyPortRaw" to JsonPrimitive("12345"),
        "localHttpProxyPort" to JsonPrimitive(12_346),
        "logRetentionDays" to JsonPrimitive(30),
        "proxyServerListColumns" to JsonPrimitive(3),
        "tunIpv4Cidr" to JsonPrimitive("10.20.0.1/30"),
        "tunIpv6Cidr" to JsonPrimitive("fd00:20::1/126"),
        "tunVpnDns" to JsonPrimitive("1.1.1.1"),
        "languageTag" to JsonPrimitive("ru"),
        "themeMode" to JsonPrimitive("Dark"),
        "localProxyAuthMode" to JsonPrimitive("Manual"),
        "localHttpProxyAuthMode" to JsonPrimitive("Manual"),
    )

    /** Switches of this machine: a backup must not turn them on elsewhere (see the other backup tests). */
    private val machineBound = mapOf(
        "enableTunMode" to "TUN is switched on per machine",
        "launchOnLogin" to "autostart is per machine",
        "useSystemProxy" to "the system proxy is per machine",
        "useWindowsSystemProxy" to "the system proxy is per machine",
        "enableLocalHttpProxy" to "follows the system proxy switch",
        "installationUuid" to "identifies this installation",
        "onboardingCompleted" to "this install keeps its own onboarding state",
        "autoConnectOnAppOpen" to "connecting at start is per machine",
        "nextRouteRuleId" to "counted again from the restored rules",
    )

    /**
     * Desktop preferences the backup does not carry today. They are real settings, so they are listed rather than
     * hidden: adding one to the backup is an optional new field and the entry here then goes away.
     */
    private val notInTheBackupYet = mapOf(
        "closeToBackground" to "window behaviour",
        "compactHome" to "Home layout",
        "sidebarCollapsed" to "window layout",
        "showConnectionLog" to "Home layout",
        "navigationStyle" to "window layout",
        "proxyServerListColumns" to "Desktop list layout; the backup holds only Android's proxyServerListLayout",
    )

    private val nullStrings = mutableSetOf<String>()
    private val keepDefault = mutableSetOf<String>()
    private val missingKey = Regex("key '(\\w+)'")
    private val badName = Regex("name '([^']+)'")

    private fun changed(index: Int, key: String, value: JsonElement, flipBit: Int): JsonElement = when {
        key in acceptedValues -> acceptedValues.getValue(key)
        key in keepDefault -> value
        value is JsonNull -> if (key in nullStrings) JsonPrimitive("text#$index") else JsonPrimitive(index + 11)
        value is JsonPrimitive && value.booleanOrNull != null ->
            JsonPrimitive(if ((index shr flipBit) and 1 == 1) !value.boolean else value.boolean)
        value is JsonPrimitive && value.isString && value.content.toLongOrNull() != null ->
            JsonPrimitive((value.content.toLong() + index + 1).toString())
        value is JsonPrimitive && value.isString -> JsonPrimitive(value.content + "#" + index)
        value is JsonPrimitive && value.longOrNull != null -> JsonPrimitive(value.longOrNull!! + index + 1)
        else -> value
    }

    /** Builds settings with every field changed; a field whose type the guess got wrong is retried as the other one. */
    private fun everythingChanged(defaults: JsonObject, flipBit: Int): Pair<JsonObject, DesktopAppSettings> {
        repeat(60) {
            val sent = JsonObject(
                defaults.entries.mapIndexed { index, (key, value) -> key to changed(index, key, value, flipBit) }.toMap(),
            )
            try {
                return sent to json.decodeFromJsonElement<DesktopAppSettings>(sent)
            } catch (error: Exception) {
                val message = error.message.orEmpty()
                val key = missingKey.find(message)?.groupValues?.get(1)
                if (key != null) {
                    nullStrings += key
                } else {
                    val name = badName.find(message)?.groupValues?.get(1) ?: throw error
                    keepDefault += sent.entries.first { (it.value as? JsonPrimitive)?.content == name }.key
                }
            }
        }
        error("could not build settings with every field changed")
    }

    @Test
    fun everySettingComesBackExceptTheOnesNamedAbove() {
        val defaults = json.encodeToJsonElement(DesktopAppSettings()).jsonObject
        val differing = mutableSetOf<String>()
        for (flipBit in 0 until 8) {
            val (sent, settings) = everythingChanged(defaults, flipBit)
            val backup = encodeAppBackup(
                DesktopUserData(settings = settings).toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1),
            )
            val restored = decodeAppBackup(backup).toDesktopRestorePreview(DesktopUserData()).userData.settings

            val got = json.encodeToJsonElement(restored).jsonObject
            differing += sent.keys.filter { key -> sent[key] != got[key] }
        }

        val expected = machineBound.keys + notInTheBackupYet.keys
        assertEquals(
            expected,
            differing,
            "settings that did not come back: ${(differing - expected).sorted()}; " +
                "named above but coming back: ${(expected - differing).sorted()}",
        )
    }
}
