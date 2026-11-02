// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The system proxy used to spawn about ten reg.exe processes and a PowerShell with an inline C# compile on
 * every connect (~0.9 s). The in-process Win32 calls must behave exactly like those tools did.
 */
class NativeWindowsSystemProxyTest {
    private val windows = isDesktopWindowsSystemProxySupported()
    private val registry = NativeDesktopWindowsRegistry()
    private val key = "HKCU\\Software\\SKIPI\\NativeRegistryTest-${UUID.randomUUID()}"

    private inline fun onWindows(block: () -> Unit) {
        if (!windows) return
        try {
            block()
        } finally {
            ProcessBuilder("reg.exe", "delete", key, "/f").redirectErrorStream(true).start().also {
                it.inputStream.readBytes()
                it.waitFor()
            }
        }
    }

    @Test
    fun theInternetSettingsRefreshSucceedsInProcess() {
        if (!windows) return
        NativeWinInetSettingsRefresher().refresh().getOrThrow()
    }

    @Test
    fun aMissingValueOrKeyReadsAsNull() = onWindows {
        assertNull(registry.read(key, "Nope").getOrThrow())
        registry.write(key, "Other", DesktopWindowsRegistryValue.string("x")).getOrThrow()
        assertNull(registry.read(key, "Nope").getOrThrow())
    }

    @Test
    fun stringsRoundTripIncludingSeparatorsSpacesAndTheEmptyString() = onWindows {
        val proxy = "http=127.0.0.1:10809;https=127.0.0.1:10809"
        registry.write(key, "ProxyServer", DesktopWindowsRegistryValue.string(proxy)).getOrThrow()
        registry.write(key, "ProxyOverride", DesktopWindowsRegistryValue.string("localhost;<local>; 10.*")).getOrThrow()
        registry.write(key, "Empty", DesktopWindowsRegistryValue.string("")).getOrThrow()

        assertEquals(DesktopWindowsRegistryValue.string(proxy), registry.read(key, "ProxyServer").getOrThrow())
        assertEquals("localhost;<local>; 10.*", registry.read(key, "ProxyOverride").getOrThrow()?.data)
        assertEquals(DesktopWindowsRegistryValue.string(""), registry.read(key, "Empty").getOrThrow())
    }

    @Test
    fun dwordsAreReadInTheHexFormReguExeUsedAndWrittenFromDecimalOrHex() = onWindows {
        registry.write(key, "ProxyEnable", DesktopWindowsRegistryValue.dword(1)).getOrThrow()
        assertEquals(DesktopWindowsRegistryValue(DesktopWindowsRegistryValueType.Dword, "0x1"), registry.read(key, "ProxyEnable").getOrThrow())

        registry.write(key, "ProxyEnable", DesktopWindowsRegistryValue(DesktopWindowsRegistryValueType.Dword, "0x1f")).getOrThrow()
        assertEquals("0x1f", registry.read(key, "ProxyEnable").getOrThrow()?.data)

        registry.write(key, "ProxyEnable", DesktopWindowsRegistryValue.dword(0)).getOrThrow()
        assertEquals("0x0", registry.read(key, "ProxyEnable").getOrThrow()?.data)
    }

    @Test
    fun deleteRemovesTheValueAndIsIdempotent() = onWindows {
        registry.write(key, "ProxyEnable", DesktopWindowsRegistryValue.dword(1)).getOrThrow()
        registry.delete(key, "ProxyEnable").getOrThrow()
        assertNull(registry.read(key, "ProxyEnable").getOrThrow())
        registry.delete(key, "ProxyEnable").getOrThrow()
        registry.delete("HKCU\\Software\\SKIPI\\NeverCreated-${UUID.randomUUID()}", "X").getOrThrow()
    }

    @Test
    fun aKeyOutsideTheUserHiveIsRefused() {
        if (!windows) return
        assertTrue(registry.write("HKLM\\Software\\SKIPI\\X", "V", DesktopWindowsRegistryValue.string("x")).isFailure)
    }

    @Test
    fun itReadsTheRealInternetSettingsLikeRegExe() {
        if (!windows) return
        val internetSettings = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        val viaReg = RegExeDesktopWindowsRegistry(ProcessDesktopWindowsCommandRunner)
        listOf("ProxyEnable", "ProxyServer", "ProxyOverride", "DoesNotExist").forEach { name ->
            assertEquals(viaReg.read(internetSettings, name).getOrThrow(), registry.read(internetSettings, name).getOrThrow(), name)
        }
    }
}
