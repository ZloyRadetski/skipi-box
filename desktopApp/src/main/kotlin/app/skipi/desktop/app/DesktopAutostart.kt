// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The XDG autostart entry that starts the application hidden when the user logs in. */
internal fun desktopAutostartEntry(launcher: String): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=SKIPI")
    appendLine("Exec=\"${launcher.escapeDesktopExecArgument()}\" --hidden")
    appendLine("Terminal=false")
    appendLine("X-GNOME-Autostart-enabled=true")
}

// Inside quotes the spec escapes four characters; the string-value rule then doubles each backslash.
private fun String.escapeDesktopExecArgument(): String = buildString {
    for (character in this@escapeDesktopExecArgument) {
        if (character == '"' || character == '`' || character == '$') append("\\\\")
        if (character == '\\') append("\\\\\\\\") else append(character)
    }
}

/**
 * What to register for start at login. An AppImage is mounted at a temporary path that is gone
 * after it exits, so it registers the AppImage file itself ([appImagePath], the `APPIMAGE`
 * variable). Otherwise only the packaged launcher qualifies: running under Gradle or a bare JVM
 * would make the login start `java` with no arguments.
 */
internal fun resolveAutostartLauncher(
    command: String?,
    appImagePath: String? = System.getenv("APPIMAGE"),
): Result<String> = runCatching {
    appImagePath?.takeIf(String::isNotBlank)?.takeIf { path -> Files.isRegularFile(Path.of(path)) }
        ?.let { return@runCatching it }
    val launcher = command?.takeIf(String::isNotBlank) ?: error("The application launcher is unknown")
    val name = Path.of(launcher).fileName?.toString().orEmpty()
    val bareName = name.removeSuffix(".exe").removeSuffix(".EXE")
    require(!bareName.equals("java", ignoreCase = true) && !bareName.equals("javaw", ignoreCase = true)) {
        "Start at login needs the packaged SKIPI launcher, not $name"
    }
    launcher
}

/** Start at login, registered with whatever the operating system uses for it. */
internal interface DesktopAutostartControl {
    fun isEnabled(): Boolean

    fun apply(enabled: Boolean, launcher: String): Result<Unit>
}

/**
 * Windows registers per-user autostart in the registry, macOS in a LaunchAgent, everything else in
 * the XDG autostart directory.
 */
internal fun desktopAutostartFor(
    windows: Boolean = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true),
    mac: Boolean = System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true),
): DesktopAutostartControl = when {
    windows -> DesktopWindowsAutostart()
    mac -> DesktopMacAutostart()
    else -> DesktopAutostart()
}

private const val MacAgentLabel = "app.skipi.desktop"

private fun String.escapeXml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** The launchd job that starts the application hidden when the user logs in. */
internal fun macAutostartPlist(launcher: String): String = buildString {
    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
    appendLine("<plist version=\"1.0\">")
    appendLine("<dict>")
    appendLine("  <key>Label</key>")
    appendLine("  <string>$MacAgentLabel</string>")
    appendLine("  <key>ProgramArguments</key>")
    appendLine("  <array>")
    appendLine("    <string>${launcher.escapeXml()}</string>")
    appendLine("    <string>--hidden</string>")
    appendLine("  </array>")
    appendLine("  <key>RunAtLoad</key>")
    appendLine("  <true/>")
    appendLine("</dict>")
    appendLine("</plist>")
}

/** Registers or removes the LaunchAgent in the user's `~/Library/LaunchAgents`. */
internal class DesktopMacAutostart(
    private val launchAgents: Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents"),
) : DesktopAutostartControl {
    private val entry: Path get() = launchAgents.resolve("$MacAgentLabel.plist")

    override fun isEnabled(): Boolean = Files.isRegularFile(entry)

    override fun apply(enabled: Boolean, launcher: String): Result<Unit> = runCatching {
        if (!enabled) {
            Files.deleteIfExists(entry)
            return@runCatching
        }
        Files.createDirectories(launchAgents)
        val temporary = entry.resolveSibling("$MacAgentLabel.plist.tmp")
        Files.writeString(temporary, macAutostartPlist(launcher))
        try {
            Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/** Registers or removes the login autostart entry under the user's XDG config directory. */
internal class DesktopAutostart(
    private val configHome: Path = defaultConfigHome(),
) : DesktopAutostartControl {
    private val entry: Path get() = configHome.resolve("autostart").resolve("skipi.desktop")

    override fun isEnabled(): Boolean = Files.isRegularFile(entry)

    override fun apply(enabled: Boolean, launcher: String): Result<Unit> = runCatching {
        if (!enabled) {
            Files.deleteIfExists(entry)
            return@runCatching
        }
        Files.createDirectories(entry.parent)
        val temporary = entry.resolveSibling("skipi.desktop.tmp")
        Files.writeString(temporary, desktopAutostartEntry(launcher))
        try {
            Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun defaultConfigHome(): Path = System.getenv("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".config")
    }
}
