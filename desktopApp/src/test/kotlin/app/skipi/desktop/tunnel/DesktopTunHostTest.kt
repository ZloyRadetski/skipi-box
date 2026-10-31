// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTunHostTest {
    private val directory = Path.of("/opt/skipi/resources")
    private fun runtime(hev: Boolean = true) = DesktopCoreRuntime(
        directory = directory,
        library = directory.resolve("libskipicore.so"),
        geoIp = directory.resolve("geoip.dat"),
        geoSite = directory.resolve("geosite.dat"),
        hevLibrary = directory.resolve("libhev-socks5-tunnel.so").takeIf { hev },
    )

    private fun windowsRuntime(hev: Boolean = true) = DesktopCoreRuntime(
        directory = directory,
        library = directory.resolve("skipicore.dll"),
        geoIp = directory.resolve("geoip.dat"),
        geoSite = directory.resolve("geosite.dat"),
        hevExecutable = directory.resolve("hev").resolve("hev-socks5-tunnel.exe").takeIf { hev },
    )

    private fun windowsHost(
        runtime: Result<DesktopCoreRuntime> = Result.success(windowsRuntime()),
        probe: () -> String? = { null },
        elevated: Boolean = true,
    ) = DesktopTunHost(
        isLinux = false,
        isWindows = true,
        runtime = { runtime },
        coreProbe = probe,
        device = { error("not used") },
        hev = { error("not used") },
        isElevated = { elevated },
    )

    private fun host(
        linux: Boolean = true,
        runtime: Result<DesktopCoreRuntime> = Result.success(runtime()),
        probe: () -> String? = { null },
    ) = DesktopTunHost(
        isLinux = linux,
        runtime = { runtime },
        coreProbe = probe,
        device = { error("not used") },
        hev = { error("not used") },
    )

    @Test
    fun isAvailableWhenEverythingIsPresent() {
        assertNull(host().unavailableReason())
    }

    @Test
    fun otherSystemsAreNotSupportedYet() {
        assertTrue("Linux" in host(linux = false).unavailableReason().orEmpty())
    }

    @Test
    fun aMissingRuntimeIsReportedWithItsMessage() {
        val reason = host(runtime = Result.failure(IllegalStateException("runtime not found"))).unavailableReason()

        assertEquals("runtime not found", reason)
    }

    @Test
    fun aRuntimeWithoutHevIsReported() {
        val reason = host(runtime = Result.success(runtime(hev = false))).unavailableReason()

        assertTrue("libhev-socks5-tunnel" in reason.orEmpty())
    }

    @Test
    fun theCoreProbeReasonIsPassedThrough() {
        assertEquals("NetworkManager is not running", host(probe = { "NetworkManager is not running" }).unavailableReason())
    }

    @Test
    fun theSettingsPageGetsNoReasonWhenTunIsAvailable() {
        // Regression: "available" is a null reason and must not be mistaken for a missing host.
        assertNull(tunUnavailableReasonFor(host()))
    }

    @Test
    fun theSettingsPageGetsTheReasonOfAnUnavailableHost() {
        assertEquals("no NM", tunUnavailableReasonFor(host(probe = { "no NM" })))
    }

    @Test
    fun aMissingHostIsReportedAsUnavailable() {
        assertTrue(tunUnavailableReasonFor(null).orEmpty().isNotBlank())
    }

    @Test
    fun theCoreIsNotProbedWhenAnEarlierCheckFails() {
        var probed = false
        host(linux = false, probe = { probed = true; null }).unavailableReason()
        host(runtime = Result.success(runtime(hev = false)), probe = { probed = true; null }).unavailableReason()

        assertEquals(false, probed)
    }

    @Test
    fun windowsIsSupportedAndAvailableWithTheHevExecutable() {
        val host = windowsHost()

        assertTrue(host.hostSupported)
        assertNull(host.unavailableReason())
    }

    @Test
    fun windowsWithoutTheHevExecutableReportsIt() {
        val reason = windowsHost(runtime = Result.success(windowsRuntime(hev = false))).unavailableReason()

        assertTrue("hev-socks5-tunnel.exe" in reason.orEmpty(), reason)
    }

    @Test
    fun windowsPassesTheCoreProbeReasonThrough() {
        assertEquals("needs administrator", windowsHost(probe = { "needs administrator" }).unavailableReason())
    }

    @Test
    fun onlyAnUnelevatedWindowsHostNeedsElevation() {
        assertTrue(windowsHost(elevated = false).needsElevation())
        assertEquals(false, windowsHost(elevated = true).needsElevation())
        assertEquals(false, host().needsElevation())
    }
}
