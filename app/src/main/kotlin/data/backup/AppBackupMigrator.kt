// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

internal fun AppBackupFile.migrateAppBackup(): AppBackupFile {
    require(format == AppBackupFormat) {
        "Invalid backup file format"
    }
    require(version in 1..CurrentAppBackupVersion) {
        if (version > CurrentAppBackupVersion) {
            "Backup file was created by a newer version"
        } else {
            "Unsupported backup file version"
        }
    }

    // Apply migrations in order. Each branch should fall through to the
    // next version step if additional migrations are added in the future.
    return when {
        version == CurrentAppBackupVersion -> this
        // v1 is the only version that exists today; the require() above
        // already rejects anything < 1, so reaching here is impossible
        // until a new version is introduced. Future migrations go here:
        // version < 2 -> copy(data = ...).copy(version = 2).migrateAppBackup()
        else -> this.copy(version = CurrentAppBackupVersion)
    }
}
