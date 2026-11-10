// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.DesktopUpdatePackage
import java.nio.file.Files
import java.nio.file.Path

/** What decides which release asset replaces the running build. Read once; tests pass their own. */
internal data class DesktopRuntimeFacts(
    val osName: String,
    val osArch: String,
    /** The launcher this process started from. */
    val executable: String?,
    /** `$APPIMAGE`: set by an AppImage runtime to the file it was started from. */
    val appImage: String?,
    val programFilesDirs: List<String>,
    val hasDpkg: Boolean,
    val hasRpm: Boolean,
) {
    companion object {
        fun current(): DesktopRuntimeFacts = DesktopRuntimeFacts(
            osName = System.getProperty("os.name").orEmpty(),
            osArch = System.getProperty("os.arch").orEmpty(),
            executable = ProcessHandle.current().info().command().orElse(null),
            appImage = System.getenv("APPIMAGE")?.takeIf(String::isNotBlank),
            programFilesDirs = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")),
            hasDpkg = Files.exists(Path.of("/usr/bin/dpkg")),
            hasRpm = Files.exists(Path.of("/usr/bin/rpm")),
        )
    }
}

private val X64 = setOf("amd64", "x86_64", "x64")

/**
 * The package this build was installed from, or null when no release asset replaces it (an architecture the
 * releases do not cover, or a launcher the app cannot place): then the user is only pointed at the release page.
 */
internal fun detectDesktopUpdatePackage(facts: DesktopRuntimeFacts): DesktopUpdatePackage? {
    val os = facts.osName.lowercase()
    val arch = facts.osArch.lowercase()
    val executable = facts.executable ?: return null
    return when {
        "windows" in os -> when {
            arch !in X64 -> null
            facts.programFilesDirs.any { dir -> executable.isInsideWindowsDirectory(dir) } -> DesktopUpdatePackage.WindowsMsi
            else -> DesktopUpdatePackage.WindowsPortable
        }
        "linux" in os -> when {
            arch !in X64 -> null
            facts.appImage != null -> DesktopUpdatePackage.LinuxAppImage
            executable.startsWith("/opt/") && facts.hasDpkg -> DesktopUpdatePackage.LinuxDeb
            executable.startsWith("/opt/") && facts.hasRpm -> DesktopUpdatePackage.LinuxRpm
            else -> DesktopUpdatePackage.LinuxPortable
        }
        "mac" in os -> if (arch == "aarch64" || arch == "arm64") DesktopUpdatePackage.MacDmg else null
        else -> null
    }
}

private fun String.isInsideWindowsDirectory(directory: String): Boolean {
    val folder = directory.trimEnd('\\', '/').lowercase()
    if (folder.isEmpty()) return false
    return lowercase().replace('/', '\\').startsWith(folder + "\\")
}
