// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path

/**
 * The loaded library, or [empty] when it could not be read (broken JSON, a file locked for a moment). The app then
 * goes on empty and its next save replaces the file, so the unreadable one is first copied next to it
 * (`<name>.unreadable-<millis>`): the user's servers, subscriptions or profiles can still be recovered from it.
 */
internal fun <T> Result<T>.orEmptyKeepingUnreadable(
    path: Path,
    now: () -> Long = System::currentTimeMillis,
    empty: () -> T,
): T = getOrElse { error ->
    val copy = path.resolveSibling("${path.fileName}.unreadable-${now()}")
    val kept = runCatching { if (Files.exists(path)) Files.copy(path, copy) else null }
    DesktopLogger.error(
        "Library",
        "Could not read $path; starting without it" +
            (kept.getOrNull()?.let { ", a copy is kept at $it" } ?: kept.exceptionOrNull()?.let { ", and no copy could be kept: ${it.message}" }.orEmpty()),
        error,
    )
    empty()
}
