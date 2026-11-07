// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSessionBusTest {
    private val runDir = "/run/user/1000"

    @Test
    fun theSessionBusAddressFromTheEnvironmentIsUsedWhenItsSocketExists() {
        val env = mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=$runDir/bus,guid=abc")

        assertEquals("unix:path=$runDir/bus,guid=abc", desktopSessionBusAddress(env) { it == "$runDir/bus" })
    }

    @Test
    fun withoutTheVariableTheUserRuntimeSocketIsTried() {
        val env = mapOf("XDG_RUNTIME_DIR" to runDir)

        assertEquals("unix:path=$runDir/bus", desktopSessionBusAddress(env) { it == "$runDir/bus" })
    }

    @Test
    fun noSocketMeansNoBusInsteadOfAnOverNineSecondWait() {
        val env = mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=$runDir/bus", "XDG_RUNTIME_DIR" to runDir)

        assertNull(desktopSessionBusAddress(env) { false })
        assertNull(desktopSessionBusAddress(emptyMap()) { true })
    }

    @Test
    fun anAbstractSocketCannotBeCheckedSoItIsTried() {
        val env = mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:abstract=/tmp/dbus-abc,guid=1")

        assertEquals("unix:abstract=/tmp/dbus-abc,guid=1", desktopSessionBusAddress(env) { false })
    }

    @Test
    fun anExplicitAddressIsCheckedTheSameWay() {
        assertNull(desktopBusAddressIfReachable("unix:path=/nonexistent/skipi-test-bus") { false })
        assertEquals("unix:path=/x/bus", desktopBusAddressIfReachable("unix:path=/x/bus") { it == "/x/bus" })
    }
}
