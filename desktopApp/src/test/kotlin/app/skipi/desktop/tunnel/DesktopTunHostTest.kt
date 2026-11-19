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
    private val stubDevice = object : DesktopTunDevice {
        override fun prepare(interfaceName: String) = Result.success(Unit)
        override fun open(planJson: String) = Result.success(5)
        override fun close() = Result.success(Unit)
    }
    // Linux: hev is linked into libskipicore.so, so the runtime ships no separate hev file.
    private fun runtime() = DesktopCoreRuntime(
        directory = directory,
        library = directory.resolve("libskipicore.so"),
        geoIp = directory.resolve("geoip.dat"),
        geoSite = directory.resolve("geosite.dat"),
    )

    private val hevDirectory: Path by lazy {
        java.nio.file.Files.createTempDirectory("skipi-hev-test").also { dir ->
            listOf("hev-socks5-tunnel.exe", "msys-2.0.dll", "msys-gcc_s-seh-1.dll", "wintun.dll")
                .forEach { name -> java.nio.file.Files.writeString(dir.resolve(name), "x") }
        }
    }

    private fun windowsRuntime(hev: Boolean = true) = DesktopCoreRuntime(
        directory = directory,
        library = directory.resolve("skipicore.dll"),
        geoIp = directory.resolve("geoip.dat"),
        geoSite = directory.resolve("geosite.dat"),
        hevExecutable = hevDirectory.resolve("hev-socks5-tunnel.exe").takeIf { hev },
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
        device = { stubDevice },
        isElevated = { elevated },
    )

    private fun macRuntime(hev: Boolean = true) = DesktopCoreRuntime(
        directory = directory,
        library = directory.resolve("libskipicore.dylib"),
        geoIp = directory.resolve("geoip.dat"),
        geoSite = directory.resolve("geosite.dat"),
        hevExecutable = directory.resolve("hev").resolve("hev-socks5-tunnel").takeIf { hev },
    )

    private fun macHost(
        runtime: Result<DesktopCoreRuntime> = Result.success(macRuntime()),
        probe: () -> String? = { null },
    ) = DesktopTunHost(
        isLinux = false,
        isMac = true,
        runtime = { runtime },
        coreProbe = probe,
        device = { stubDevice },
    )

    private fun host(
        linux: Boolean = true,
        runtime: Result<DesktopCoreRuntime> = Result.success(runtime()),
        probe: () -> String? = { null },
    ) = DesktopTunHost(
        isLinux = linux,
        runtime = { runtime },
        coreProbe = probe,
        device = { stubDevice },
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
    fun linuxNeedsNoSeparateHevFileBecauseCoreCarriesIt() {
        assertNull(host(runtime = Result.success(runtime())).unavailableReason())
    }

    @Test
    fun linuxRunsHevInsideCoreAndWindowsHandsCoreTheExecutable() {
        val linux = host().tunRuntime()
        assertNull(linux.coreHevPlan(sampleRequest(), tunFd = 7).executable)

        val windows = windowsHostWithDevice().tunRuntime()
        assertEquals(
            hevDirectory.resolve("hev-socks5-tunnel.exe").toString(),
            windows.coreHevPlan(sampleRequest(), tunFd = -1).executable,
        )
    }

    @Test
    fun macHandsCoreTheExecutableTheRunDirectoryAndTheInterfacePlan() {
        val runDirectory = directory.resolve("run")
        val mac = DesktopTunHost(
            isLinux = false,
            isMac = true,
            runtime = { Result.success(macRuntime()) },
            coreProbe = { null },
            device = { stubDevice },
            macRunDirectory = { runDirectory },
        ).tunRuntime()

        val plan = mac.coreHevPlan(sampleRequest(), tunFd = -1)

        assertEquals(null, plan.tunFd)
        assertEquals(directory.resolve("hev").resolve("hev-socks5-tunnel").toString(), plan.executable)
        assertEquals(runDirectory.toString(), plan.runDirectory)
        assertEquals("skipi0", plan.macInterface?.name)
        assertEquals(listOf("172.19.0.1/30"), plan.macInterface?.addresses)
    }

    @Test
    fun theCoreHostedRuntimeReadsTheCoreSessionState() {
        var sessionRunning = true
        val runtime = DesktopTunHost(
            isLinux = true,
            runtime = { Result.success(runtime()) },
            coreProbe = { null },
            device = { object : DesktopTunDevice {
                override fun prepare(interfaceName: String) = Result.success(Unit)
                override fun open(planJson: String) = Result.success(5)
                override fun close() = Result.success(Unit)
            } },
                coreSessionRunning = { sessionRunning },
        ).tunRuntime()
        runtime.attachForCore(sampleRequest().plan).getOrThrow()

        assertTrue(runtime.isRunning())
        sessionRunning = false
        assertEquals(false, runtime.isRunning())
    }

    private fun sampleRequest() = DesktopTunStartRequest(
        plan = engine.vpn.TunInterfacePlan(
            name = "skipi0",
            mtu = 1400,
            addresses = listOf(engine.network.NetworkCidrAddress("172.19.0.1", 30)),
            routes = listOf(engine.network.NetworkCidrAddress("0.0.0.0", 1)),
        ),
        socksAddress = "127.0.0.1",
        socksPort = 10808,
        socksUsername = "",
        socksPassword = "",
        logPath = "",
    )

    private fun windowsHostWithDevice() = DesktopTunHost(
        isLinux = false,
        isWindows = true,
        runtime = { Result.success(windowsRuntime()) },
        coreProbe = { null },
        device = { stubDevice },
        isElevated = { true },
    )

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
        host(runtime = Result.failure(IllegalStateException("no runtime")), probe = { probed = true; null }).unavailableReason()

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
    fun windowsWithoutAHevRuntimeLibraryReportsIt() {
        val dir = java.nio.file.Files.createTempDirectory("skipi-hev-missing")
        java.nio.file.Files.writeString(dir.resolve("hev-socks5-tunnel.exe"), "x")
        listOf("msys-2.0.dll", "wintun.dll").forEach { java.nio.file.Files.writeString(dir.resolve(it), "x") }
        val runtime = windowsRuntime().copy(hevExecutable = dir.resolve("hev-socks5-tunnel.exe"))

        val reason = windowsHost(runtime = Result.success(runtime)).unavailableReason()

        assertTrue("msys-gcc_s-seh-1.dll" in reason.orEmpty(), reason)
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

    @Test
    fun macIsSupportedAndAvailableWithTheHevExecutable() {
        val host = macHost()

        assertTrue(host.hostSupported)
        assertNull(host.unavailableReason())
    }

    @Test
    fun macAsksForAdministratorRightsWhenTheTunnelStartsNotUpFront() {
        // The password prompt belongs to the moment TUN is switched on; the app itself is never elevated.
        assertEquals(false, macHost().needsElevation())
    }

    @Test
    fun macWithoutTheHevExecutableReportsIt() {
        val reason = macHost(runtime = Result.success(macRuntime(hev = false))).unavailableReason()

        assertTrue("hev-socks5-tunnel" in reason.orEmpty(), reason)
    }

    @Test
    fun macPassesTheCoreProbeReasonThrough() {
        assertEquals("not allowed", macHost(probe = { "not allowed" }).unavailableReason())
    }
}
