// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUrlSchemeTest {
    private val windowsLauncher = "C:\\Program Files\\SKIPI\\SKIPI.exe"
    private val schemeKey = "HKCU\\Software\\Classes\\skipi"
    private val commandKey = "$schemeKey\\shell\\open\\command"

    @Test
    fun windowsRegistersTheSchemeForTheCurrentUser() {
        val ran = mutableListOf<List<String>>()
        val scheme = DesktopWindowsUrlScheme(run = { command ->
            ran += command
            if (command[1] == "query") Result.failure(IllegalStateException("not found")) else Result.success("")
        })

        assertTrue(scheme.register(windowsLauncher).getOrThrow())

        assertEquals(
            listOf(
                listOf("reg", "query", commandKey, "/ve"),
                listOf("reg", "add", schemeKey, "/ve", "/t", "REG_SZ", "/d", "URL:SKIPI", "/f"),
                listOf("reg", "add", schemeKey, "/v", "URL Protocol", "/t", "REG_SZ", "/f"),
                // The command goes last: a registration cut short is repeated at the next start.
                listOf(
                    "reg", "add", commandKey, "/ve", "/t", "REG_SZ",
                    "/d", "\"\\\"$windowsLauncher\\\" \\\"%1\\\"\"", "/f",
                ),
            ),
            ran,
        )
    }

    @Test
    fun windowsLeavesACurrentRegistrationAloneAndRewritesAMovedOne() {
        var registered = "    (Default)    REG_SZ    \"$windowsLauncher\" \"%1\""
        val ran = mutableListOf<List<String>>()
        val scheme = DesktopWindowsUrlScheme(run = { command ->
            ran += command
            Result.success(if (command[1] == "query") registered else "")
        })

        assertFalse(scheme.register(windowsLauncher).getOrThrow())
        assertEquals(1, ran.size)

        registered = "    (Default)    REG_SZ    \"D:\\old\\SKIPI.exe\" \"%1\""
        assertTrue(scheme.register(windowsLauncher).getOrThrow())
    }

    @Test
    fun windowsRefusesALauncherPathWithAQuote() {
        val scheme = DesktopWindowsUrlScheme(run = { Result.failure(IllegalStateException("unused")) })

        assertTrue(scheme.register("C:\\a\"b\\SKIPI.exe").isFailure)
    }

    @Test
    fun linuxWritesAUrlHandlerEntryAndMakesItTheDefaultOnce() = withTemporaryDirectory { dataHome ->
        val ran = mutableListOf<List<String>>()
        val scheme = DesktopLinuxUrlScheme(dataHome, run = { command -> ran += command; Result.success("") })

        assertTrue(scheme.register("/opt/SKIPI Apps/100%/SKIPI.AppImage").getOrThrow())

        val entry = Files.readString(dataHome.resolve("applications/skipi-url-handler.desktop"))
        assertTrue(entry.contains("Exec=\"/opt/SKIPI Apps/100%%/SKIPI.AppImage\" %u\n"), entry)
        assertTrue(entry.contains("MimeType=x-scheme-handler/skipi;\n"), entry)
        assertTrue(entry.contains("NoDisplay=true\n"), entry)
        assertEquals(
            listOf(listOf("xdg-mime", "default", "skipi-url-handler.desktop", "x-scheme-handler/skipi")),
            ran,
        )

        // A second start changes nothing, so a handler the user picked since then stays.
        assertFalse(scheme.register("/opt/SKIPI Apps/100%/SKIPI.AppImage").getOrThrow())
        assertEquals(1, ran.size)
    }

    @Test
    fun linuxRetriesAtTheNextStartWhenTheDefaultCouldNotBeSet() = withTemporaryDirectory { dataHome ->
        var available = false
        val scheme = DesktopLinuxUrlScheme(dataHome, run = {
            if (available) Result.success("") else Result.failure(IllegalStateException("no xdg-mime"))
        })

        assertTrue(scheme.register("/usr/bin/SKIPI").isFailure)
        available = true

        assertTrue(scheme.register("/usr/bin/SKIPI").getOrThrow())
    }

    @Test
    fun theMacBundleDeclaresTheScheme() {
        val build = File("build.gradle.kts").readText()

        assertTrue(build.contains("CFBundleURLTypes"))
        assertTrue(build.contains("<string>skipi</string>"))
    }

    private fun withTemporaryDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("skipi-url-scheme-")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
