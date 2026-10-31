// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.backup.AppBackupFile
import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun defaultDesktopBackupFileName(now: Date = Date()): String =
    "skipi-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)}.json"

internal fun writeDesktopBackup(path: Path, backup: AppBackupFile) {
    path.parent?.let(Files::createDirectories)
    Files.writeString(path, encodeAppBackup(backup), StandardCharsets.UTF_8)
}

internal fun readDesktopBackup(path: Path): AppBackupFile =
    decodeAppBackup(Files.readString(path, StandardCharsets.UTF_8))

/** Asks where to save the backup; null means the user cancelled. */
internal fun chooseDesktopBackupTarget(title: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = defaultDesktopBackupFileName()
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name).toPath()
}

/** Asks which backup to restore; null means the user cancelled. */
internal fun chooseDesktopBackupSource(title: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name).toPath()
}
