// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Backups written by released SKIPI versions (personal data replaced, shape kept) must still restore, and what
 * they hold must survive a restore and a new export. People keep backups for months and move them between
 * versions and devices, so this is the contract the stored format has to keep.
 */
class BackupFixtureCompatibilityTest {
    private val fixtures = listOf("skipi-backup-0.4.1.json", "skipi-backup-0.5.0.json")
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("backups/$name")) { "missing fixture $name" }
            .readBytes().decodeToString()

    private fun roundTrip(text: String): String {
        val original = decodeAppBackup(text)
        val state = original.toRestorePreview().restoredState
        return encodeAppBackup(
            state.toAppBackupFile(original.createdAtMillis, original.appVersionName, original.appVersionCode),
        )
    }

    /** Every value the old file holds, at the same place in the new export; fields the file never had are free. */
    private fun lostValues(path: String, before: JsonElement?, after: JsonElement?, out: MutableList<String>) {
        when {
            before is JsonObject && after is JsonObject ->
                before.forEach { (key, value) -> lostValues("$path.$key", value, after[key], out) }
            before is JsonObject -> out += "$path: object vanished"
            before is JsonArray && after is JsonArray -> {
                if (before.size != after.size) out += "$path: ${before.size} items became ${after.size}"
                before.zip(after).forEachIndexed { index, (a, b) -> lostValues("$path[$index]", a, b, out) }
            }
            before is JsonArray -> out += "$path: list vanished"
            before == null || before is JsonNull -> Unit
            isHostRewritten(path) -> Unit
            before is JsonPrimitive && after is JsonPrimitive && sameContent(path, before, after) -> Unit
            else -> out += "$path: ${before.toString().take(50)} became ${after.toString().take(50)}"
        }
    }

    /** The user agent names the running app and host, so a restore on another host legitimately rewrites it. */
    private fun isHostRewritten(path: String): Boolean =
        Regex("""\.subscriptionGroups\[\d+]\.userAgent$""").containsMatchIn(path)

    private fun sameContent(path: String, before: JsonPrimitive, after: JsonPrimitive): Boolean {
        val a = before.contentOrNull
        val b = after.contentOrNull
        if (a == b) return true
        // A profile's text carries the app version in its user-agent line, refreshed on restore.
        return path.endsWith(".rawConfig") && a != null && b != null &&
            userAgentLine.replace(a, "") == userAgentLine.replace(b, "")
    }

    private val userAgentLine = Regex("""resource-user-agent = \S+""")

    @Test
    fun everyReleasedBackupRestoresAndKeepsAllItsValues() {
        fixtures.forEach { name ->
            val text = load(name)
            val lost = mutableListOf<String>()

            lostValues("", json.parseToJsonElement(text), json.parseToJsonElement(roundTrip(text)), lost)

            assertEquals(emptyList(), lost, "values lost restoring $name")
        }
    }

    @Test
    fun restoringTwiceChangesNothingMore() {
        fixtures.forEach { name ->
            val once = roundTrip(load(name))

            assertEquals(once, roundTrip(once), "$name is not stable under restore and export")
        }
    }

    @Test
    fun theRestoredStateHoldsEveryRecordOfTheBackup() {
        fixtures.forEach { name ->
            val backup = decodeAppBackup(load(name))
            val state = backup.toRestorePreview().restoredState

            assertEquals(backup.data.proxyServers.size, state.proxyServers.size, name)
            assertEquals(backup.data.subscriptionGroups.size, state.subscriptionGroups.size, name)
            assertEquals(backup.data.routeRules.size, state.routeRules.size, name)
            assertEquals(backup.data.trafficConfigs.size, state.trafficConfigs.size, name)
            assertEquals(backup.data.activeTrafficConfigId, state.activeTrafficConfigId, name)
            assertTrue(state.proxyServers.isNotEmpty(), name)
        }
    }
}
