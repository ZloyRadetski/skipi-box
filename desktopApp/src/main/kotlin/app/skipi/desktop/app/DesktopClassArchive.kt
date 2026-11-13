// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path

/**
 * The launcher keeps the application's class-data archive in `%LOCALAPPDATA%\SKIPI` (see the launcher options in
 * desktopApp/build.gradle.kts). The JVM writes it when the app exits, but it does not create the folder; and the
 * install folder, where the archive used to go, is read-only for a normal user under Program Files, so there the
 * archive was never made and every start loaded and verified every class again (12 s instead of 1.4 s).
 *
 * Returns the folder, or null where it is not used or cannot be made: the start must go on without it.
 */
internal fun ensureClassArchiveFolder(localAppData: String?, isWindows: Boolean): Path? {
    if (!isWindows || localAppData.isNullOrBlank()) return null
    return runCatching { Files.createDirectories(Path.of(localAppData, "SKIPI")) }.getOrNull()
}
