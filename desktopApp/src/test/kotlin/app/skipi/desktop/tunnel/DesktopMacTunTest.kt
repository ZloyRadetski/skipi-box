// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** The privileged supervisor lives in SKIPI Core; what stays here is choosing the interface and the run directory. */
class DesktopMacTunTest {
    @Test
    fun theFirstFreeUtunNumberFromNineIsChosen() {
        assertEquals("utun9", pickMacTunInterfaceName(emptyList()))
        assertEquals("utun9", pickMacTunInterfaceName(listOf("lo0", "en0", "utun0", "utun1", "utun2")))
        assertEquals("utun10", pickMacTunInterfaceName(listOf("utun0", "utun9")))
        assertEquals("utun11", pickMacTunInterfaceName(listOf("utun9", "utun10", "en0")))
    }

    @Test
    fun theInterfaceListOfIfconfigIsSplitOnSpaces() {
        assertEquals(listOf("lo0", "gif0", "en0", "utun0"), parseMacInterfaceList("lo0 gif0 en0 utun0\n"))
        assertEquals(emptyList(), parseMacInterfaceList(""))
    }

    @Test
    fun theRunDirectoryIsUnderTheUsersSkipiFolder() {
        assertEquals(Path.of(System.getProperty("user.home"), "SKIPI", "run"), macTunRunDirectory())
    }
}
