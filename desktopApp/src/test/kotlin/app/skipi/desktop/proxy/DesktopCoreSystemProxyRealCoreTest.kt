// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in (SKIPI_CORE_INTEGRATION_DIR): the system proxy through the real Core library, over the C ABI.
 * On Windows it changes the real per-user proxy for a moment and checks the user's values come back exactly.
 */
class DesktopCoreSystemProxyRealCoreTest {
    private val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private fun registryValues(): String = ProcessBuilder(
        "reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
        "/v", "ProxyEnable",
    ).redirectErrorStream(true).start().let { process ->
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val server = ProcessBuilder(
            "reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "/v", "ProxyServer",
        ).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } }
        out.trim() + "\n" + server.trim()
    }

    private fun liveSkipiOwnsTheProxy(): Boolean = ProcessBuilder(
        "reg.exe", "query", "HKCU\\Software\\SKIPI\\Desktop", "/v", "SystemProxyLease",
    ).redirectErrorStream(true).start().let { process ->
        process.inputStream.bufferedReader().readText()
        process.waitFor() == 0
    }

    @Test
    fun aRealCoreAcquiresAndGivesTheUsersProxyBackExactly() {
        val runtimeDirectory = System.getenv("SKIPI_CORE_INTEGRATION_DIR")?.takeIf(String::isNotBlank) ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        val leaseDirectory = Files.createTempDirectory("skipi-sysproxy-real")
        val manager = DesktopCoreSystemProxyManager(
            core = { controller.nativeForTun().map { native -> DesktopSystemProxyCore(native::systemProxy) } },
            directory = leaseDirectory,
        )
        val endpoints = DesktopSystemProxyEndpoints(httpPort = 58089, socksPort = 58088)

        assertTrue(manager.isSupportedHost())
        assertEquals(DesktopSystemProxyLeaseAction.NothingToRelease, manager.recover().getOrThrow().action)
        if (!windows) return // macOS and Linux would change the developer's desktop settings
        // A SKIPI that is connected right now owns the system proxy; this test must not touch it.
        if (liveSkipiOwnsTheProxy()) return

        val before = registryValues()
        try {
            assertEquals(DesktopSystemProxyLeaseAction.Acquired, manager.acquire(endpoints).getOrThrow().action)
            assertTrue(registryValues().contains("127.0.0.1:58089"), "the system proxy must point at the endpoint")
            assertEquals(DesktopSystemProxyLeaseAction.AlreadyAcquired, manager.acquire(endpoints).getOrThrow().action)

            assertEquals(DesktopSystemProxyLeaseAction.Released, manager.release().getOrThrow().action)
            assertEquals(before, registryValues(), "the user's values must come back exactly")
            assertEquals(DesktopSystemProxyLeaseAction.NothingToRelease, manager.release().getOrThrow().action)
        } finally {
            if (registryValues() != before) manager.forceClear()
        }
    }
}
