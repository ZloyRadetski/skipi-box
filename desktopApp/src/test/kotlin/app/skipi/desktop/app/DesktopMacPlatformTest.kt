// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMacPlatformTest {
    // ---- machine identity ---------------------------------------------------------------------------

    @Test
    fun theMachineIdentityIsThePlatformUuidOfIoreg() {
        val output = """
            +-o Root  <class IORegistryEntry, id 0x100000100, retain 25>
              +-o MacBookPro18,3  <class IOPlatformExpertDevice, id 0x10000020f, registered, matched, active, busy 0 (53 ms), retain 40>
                {
                  "IOPlatformSerialNumber" = "C02XXXXXXXXX"
                  "IOPlatformUUID" = "8F2B1C3D-4E5F-6071-8293-A4B5C6D7E8F9"
                  "manufacturer" = <"Apple Inc.">
                }
        """.trimIndent()

        assertEquals("8F2B1C3D-4E5F-6071-8293-A4B5C6D7E8F9", parseMacPlatformUuid(output))
    }

    @Test
    fun anUnreadableIoregReportGivesNoIdentity() {
        assertNull(parseMacPlatformUuid(""))
        assertNull(parseMacPlatformUuid("\"IOPlatformUUID\" = \"\""))
        assertNull(parseMacPlatformUuid("no uuid here"))
    }

    // ---- start at login ----------------------------------------------------------------------------------

    @Test
    fun theLaunchAgentStartsTheAppHiddenAtLogin() {
        val plist = macAutostartPlist("/Applications/SKIPI.app/Contents/MacOS/SKIPI")

        assertTrue("<key>Label</key>" in plist && "app.skipi.desktop" in plist, plist)
        assertTrue("<string>/Applications/SKIPI.app/Contents/MacOS/SKIPI</string>" in plist, plist)
        assertTrue("<string>--hidden</string>" in plist, plist)
        assertTrue("<key>RunAtLoad</key>" in plist && "<true/>" in plist, plist)
    }

    @Test
    fun aLauncherPathIsEscapedForXml() {
        val plist = macAutostartPlist("/Applications/A&B <x>/SKIPI")

        assertTrue("A&amp;B &lt;x&gt;" in plist, plist)
        assertFalse("A&B" in plist)
    }

    @Test
    fun theLaunchAgentIsWrittenToLibraryLaunchAgentsAndRemovedAgain() {
        val home = Files.createTempDirectory("skipi-mac-agents")
        try {
            val autostart = DesktopMacAutostart(launchAgents = home.resolve("Library").resolve("LaunchAgents"))
            assertFalse(autostart.isEnabled())

            autostart.apply(true, "/Applications/SKIPI.app/Contents/MacOS/SKIPI").getOrThrow()
            assertTrue(autostart.isEnabled())
            assertTrue(Files.readString(home.resolve("Library/LaunchAgents/app.skipi.desktop.plist")).contains("--hidden"))

            autostart.apply(false, "ignored").getOrThrow()
            assertFalse(autostart.isEnabled())
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun theFactoryPicksTheMacAutostartOnMacOs() {
        assertTrue(desktopAutostartFor(windows = false, mac = true) is DesktopMacAutostart)
        assertTrue(desktopAutostartFor(windows = true, mac = false) is DesktopWindowsAutostart)
    }

    // ---- Wi-Fi name -----------------------------------------------------------------------------------------

    @Test
    fun theWifiNameIsReadFromNetworksetup() {
        assertEquals("Home Net", parseMacWifiSsid("Current Wi-Fi Network: Home Net\n"))
        assertNull(parseMacWifiSsid("You are not associated with an AirPort network.\n"))
        assertNull(parseMacWifiSsid(""))
    }
}
