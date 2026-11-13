// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
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
/**
 * Ends a short-lived instance (a second start that handed its link over, an elevated relaunch) without the JVM's
 * exit work. A normal exit writes the class archive, and such an instance has loaded a few hundred classes: when
 * no valid archive exists yet, its tiny archive would be taken as valid by the next real start, which then turns
 * the automatic archive off and stays slow until the next update. Runtime.halt still writes the archive; the
 * operating system's exit does not.
 */
internal fun exitWithoutClassArchive(): Nothing {
    if (System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) {
        runCatching {
            val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
            Linker.nativeLinker()
                .downcallHandle(kernel32.findOrThrow("ExitProcess"), FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
                .invokeWithArguments(0)
        }
    }
    Runtime.getRuntime().halt(0)
    error("unreachable")
}

internal fun ensureClassArchiveFolder(localAppData: String?, isWindows: Boolean): Path? {
    if (!isWindows || localAppData.isNullOrBlank()) return null
    return runCatching { Files.createDirectories(Path.of(localAppData, "SKIPI")) }.getOrNull()
}
