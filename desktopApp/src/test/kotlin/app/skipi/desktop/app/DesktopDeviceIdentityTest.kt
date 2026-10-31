// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopDeviceIdentityTest {

    @Test
    fun generatesConsistentHwidForSameInputs() {
        val hwid1 = DesktopDeviceIdentity.computeHwid("uuid-123")
        val hwid2 = DesktopDeviceIdentity.computeHwid("uuid-123")
        assertEquals(hwid1, hwid2)
        assertEquals(64, hwid1.length)
        assertTrue(hwid1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun producesDifferentHwidForDifferentInstallationUuids() {
        val hwid1 = DesktopDeviceIdentity.computeHwid("uuid-1")
        val hwid2 = DesktopDeviceIdentity.computeHwid("uuid-2")
        assertNotEquals(hwid1, hwid2)
    }

    @Test
    fun generatesExpectedDeviceHeaders() {
        val headers = DesktopDeviceIdentity.deviceHeaders("test-uuid")
        assertEquals("SKIPI", headers["x-client"])
        assertEquals("0.4.1", headers["x-app-version"])
        assertTrue(headers["x-device-os"]!!.isNotEmpty())
        assertTrue(headers["x-ver-os"]!!.isNotEmpty())
        assertTrue(headers["x-device-model"]!!.isNotEmpty())
        assertEquals(64, headers["x-hwid"]!!.length)
        assertEquals(headers["x-hwid"], headers["X-Device-ID"])
    }

    @Test
    fun hardwareIdentifierIsNotEmpty() {
        val machineId = DesktopDeviceIdentity.getHardwareIdentifier()
        assertTrue(machineId.isNotEmpty())
    }
}
