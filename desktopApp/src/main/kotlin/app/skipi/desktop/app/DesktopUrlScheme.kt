// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Makes the operating system start this copy of SKIPI for `skipi://` links. */
internal interface DesktopUrlScheme {
    /** Registers [launcher] when the current registration does not name it, and reports whether it did. */
    fun register(launcher: String): Result<Boolean>
}

private const val WindowsSchemeKey = "HKCU\\Software\\Classes\\skipi"
private const val WindowsCommandKey = "$WindowsSchemeKey\\shell\\open\\command"

/**
 * The current user's `Software\Classes\skipi` key: it needs no administrator rights and serves the portable
 * folder as well as the installed app. The copy that started last is the one links open.
 */
internal class DesktopWindowsUrlScheme(
    private val run: (List<String>) -> Result<String> = ::runUrlSchemeCommand,
) : DesktopUrlScheme {
    override fun register(launcher: String): Result<Boolean> = runCatching {
        require('"' !in launcher) { "The launcher path contains a quote" }
        val command = "\"$launcher\" \"%1\""
        if (run(listOf("reg", "query", WindowsCommandKey, "/ve")).getOrNull()?.contains(command) == true) {
            return@runCatching false
        }
        add(WindowsSchemeKey, "/ve", "/t", "REG_SZ", "/d", "URL:SKIPI")
        add(WindowsSchemeKey, "/v", "URL Protocol", "/t", "REG_SZ")
        // Escaped for reg.exe as in the autostart value; the JDK passes a quoted argument through untouched.
        add(WindowsCommandKey, "/ve", "/t", "REG_SZ", "/d", "\"" + command.replace("\"", "\\\"") + "\"")
        true
    }

    private fun add(key: String, vararg value: String) {
        run(listOf("reg", "add", key) + value + "/f").getOrThrow()
    }
}

private const val LinuxHandlerFile = "skipi-url-handler.desktop"
private const val SchemeMimeType = "x-scheme-handler/skipi"

/** The XDG entry that handles the scheme; it is hidden from menus, the installed entry is shown there. */
internal fun desktopUrlHandlerEntry(launcher: String): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=SKIPI")
    // A literal percent sign is doubled in Exec, where a single one starts a field code.
    appendLine("Exec=\"${launcher.escapeDesktopExecArgument().replace("%", "%%")}\" %u")
    appendLine("Terminal=false")
    appendLine("NoDisplay=true")
    appendLine("MimeType=$SchemeMimeType;")
}

/**
 * A per-user handler entry made the default with `xdg-mime`, the same for deb, rpm and AppImage. It is
 * written again only when the launcher moved, so a handler the user chose later is left in place.
 */
internal class DesktopLinuxUrlScheme(
    private val dataHome: Path = defaultDataHome(),
    private val run: (List<String>) -> Result<String> = ::runUrlSchemeCommand,
) : DesktopUrlScheme {
    override fun register(launcher: String): Result<Boolean> = runCatching {
        val entry = dataHome.resolve("applications").resolve(LinuxHandlerFile)
        val content = desktopUrlHandlerEntry(launcher)
        if (runCatching { Files.readString(entry) }.getOrNull() == content) return@runCatching false
        Files.createDirectories(entry.parent)
        Files.writeString(entry, content)
        run(listOf("xdg-mime", "default", LinuxHandlerFile, SchemeMimeType)).onFailure {
            // Without the entry the next start tries again.
            Files.deleteIfExists(entry)
        }.getOrThrow()
        true
    }

    companion object {
        fun defaultDataHome(): Path = System.getenv("XDG_DATA_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".local", "share")
    }
}

/** Null on macOS, where the application bundle's Info.plist declares the scheme. */
internal fun desktopUrlSchemeFor(
    windows: Boolean = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true),
    mac: Boolean = System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true),
): DesktopUrlScheme? = when {
    windows -> DesktopWindowsUrlScheme()
    mac -> null
    else -> DesktopLinuxUrlScheme()
}

/** macOS delivers an opened link as an event, to the first start and to a running app alike, not as an argument. */
internal fun listenForOpenedLinks(onLink: (String) -> Unit) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return
    desktop.setOpenURIHandler { event -> onLink(event.uri.toString()) }
}

private fun runUrlSchemeCommand(command: List<String>): Result<String> {
    // reg.exe answers in the console's code page, not UTF-8.
    val charset = runCatching { java.nio.charset.Charset.forName(System.getProperty("native.encoding")) }
        .getOrDefault(Charsets.UTF_8)
    return runProcessWithTimeout(command, 15.seconds, charset)
}
