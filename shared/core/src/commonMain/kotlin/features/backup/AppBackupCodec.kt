// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.backup

import kotlinx.serialization.json.Json

private val appBackupJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

/** Encodes the stable, platform-independent SKIPI backup document. */
fun encodeAppBackup(backup: AppBackupFile): String =
    appBackupJson.encodeToString(backup)

/** Decodes a backup document while ignoring fields added by newer compatible writers. */
fun decodeAppBackup(content: String): AppBackupFile =
    appBackupJson.decodeFromString<AppBackupFile>(content)
