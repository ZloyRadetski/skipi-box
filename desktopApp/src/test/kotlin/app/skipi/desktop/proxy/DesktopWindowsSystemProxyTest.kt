// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowsSystemProxyTest {
    @Test
    fun acquires_and_releases_the_exact_user_proxy_snapshot() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("corp-proxy:8080"))
        fixture.registry.put(ProxyOverride, DesktopWindowsRegistryValue.string("intranet.local"))

        val acquired = fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Acquired, acquired.action)
        assertEquals(DesktopWindowsRegistryValue.dword(1), fixture.registry[ProxyEnable])
        assertEquals(
            DesktopWindowsRegistryValue.string("http=127.0.0.1:11809;https=127.0.0.1:11809"),
            fixture.registry[ProxyServer],
        )
        assertEquals(
            DesktopWindowsRegistryValue.string("intranet.local;<local>;localhost;127.*;[::1]"),
            fixture.registry[ProxyOverride],
        )
        assertTrue(fixture.registry.marker().isNullOrBlank().not())
        assertTrue(Files.exists(fixture.leasePath))

        val released = fixture.manager.release().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Released, released.action)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertEquals(DesktopWindowsRegistryValue.string("corp-proxy:8080"), fixture.registry[ProxyServer])
        assertEquals(DesktopWindowsRegistryValue.string("intranet.local"), fixture.registry[ProxyOverride])
        assertEquals(null, fixture.registry.marker())
        assertFalse(Files.exists(fixture.leasePath))
        assertEquals(2, fixture.commandRunner.refreshCalls)
        fixture.close()
    }

    @Test
    fun release_never_overwrites_proxy_changed_outside_skipi() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("new-corporate-proxy:8080"))
        val result = fixture.manager.release().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.SkippedNotOwner, result.action)
        assertEquals(DesktopWindowsRegistryValue.string("new-corporate-proxy:8080"), fixture.registry[ProxyServer])
        assertEquals(DesktopWindowsRegistryValue.dword(1), fixture.registry[ProxyEnable])
        assertEquals(null, fixture.registry.marker())
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun recover_restores_an_interrupted_active_lease() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("previous:8888"))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        val restartedManager = fixture.newManager()
        val recovered = restartedManager.recover().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Recovered, recovered.action)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertEquals(DesktopWindowsRegistryValue.string("previous:8888"), fixture.registry[ProxyServer])
        assertEquals(null, fixture.registry.marker())
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun failed_acquire_rolls_back_before_leaving_a_lease() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("previous:8888"))
        fixture.commandRunner.failNextRegistryWrite(InternetSettingsKey, ProxyEnable)

        val result = fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809))

        assertTrue(result.isFailure)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertEquals(DesktopWindowsRegistryValue.string("previous:8888"), fixture.registry[ProxyServer])
        assertEquals(null, fixture.registry.marker())
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun stale_file_without_skipi_marker_is_discarded_without_changing_windows_proxy() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(1))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("external:3128"))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()
        // Simulate an external cleanup/change after a crashed process.
        fixture.registry.deleteMarker()
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("external:3128"))

        val result = fixture.newManager().recover().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.SkippedNotOwner, result.action)
        assertEquals(DesktopWindowsRegistryValue.string("external:3128"), fixture.registry[ProxyServer])
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun keeps_the_lease_and_xray_shutdown_guard_when_wininet_refresh_fails() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()
        fixture.commandRunner.failNextRefresh()

        val failedRelease = fixture.manager.release()

        assertTrue(failedRelease.isFailure)
        // The registry has already been restored, but the retained marker/lease
        // allows the next close/recovery attempt to reissue the WinINet refresh.
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertTrue(fixture.registry.marker().isNullOrBlank().not())
        assertTrue(Files.exists(fixture.leasePath))

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Released, fixture.manager.release().getOrThrow().action)
        assertEquals(null, fixture.registry.marker())
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    // A dynamic local port changes at every start. A lease left on the old port (the app was killed, or the
    // proxy was never released) used to fail the next acquire with "already active for ...", so nothing
    // could connect again until the lease was cleared by hand.
    @Test
    fun acquiringAnotherPortRepointsTheActiveLeaseAndKeepsTheOriginalSnapshot() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("corp-proxy:8080"))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        val moved = fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 12_345))

        assertTrue(moved.isSuccess, "acquire failed: ${moved.exceptionOrNull()}")
        assertEquals(DesktopWindowsRegistryValue.dword(1), fixture.registry[ProxyEnable])
        assertEquals(
            DesktopWindowsRegistryValue.string("http=127.0.0.1:12345;https=127.0.0.1:12345"),
            fixture.registry[ProxyServer],
        )

        val released = fixture.manager.release().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Released, released.action)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertEquals(DesktopWindowsRegistryValue.string("corp-proxy:8080"), fixture.registry[ProxyServer])
        assertEquals(null, fixture.registry.marker())
        fixture.close()
    }

    @Test
    fun aRestartedAppRepointsALeaseLeftOnAnOldPort() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        val moved = fixture.newManager().acquire(DesktopWindowsHttpProxyEndpoint(port = 12_345))

        assertTrue(moved.isSuccess, "acquire failed: ${moved.exceptionOrNull()}")
        assertEquals(
            DesktopWindowsRegistryValue.string("http=127.0.0.1:12345;https=127.0.0.1:12345"),
            fixture.registry[ProxyServer],
        )
        assertEquals(DesktopWindowsSystemProxyLeaseAction.Released, fixture.newManager().release().getOrThrow().action)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        fixture.close()
    }

    // WinINet rewrites the ProxyServer value it is refreshed with ("http=H:P;https=H:P" comes back as "H:P"), so
    // the value stopped matching the lease, the release took the proxy for somebody else's and left it enabled on
    // a port nobody listened on any more: the browser said "the proxy server refuses connections".
    @Test
    fun aProxyServerValueWindowsRewroteToTheSameEndpointIsStillOurs() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("corp-proxy:8080"))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("127.0.0.1:11809"))

        val released = fixture.manager.release().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Released, released.action)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertEquals(DesktopWindowsRegistryValue.string("corp-proxy:8080"), fixture.registry[ProxyServer])
        assertEquals(null, fixture.registry.marker())
        fixture.close()
    }

    @Test
    fun aRewrittenValueOfTheSameEndpointIsStillOursAfterARestart() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("127.0.0.1:11809"))

        val recovered = fixture.newManager().recover().getOrThrow()

        assertEquals(DesktopWindowsSystemProxyLeaseAction.Recovered, recovered.action)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        fixture.close()
    }

    // The "original" settings the lease restores must not be a dead proxy of ours that an earlier session left.
    @Test
    fun aDeadLoopbackProxyIsNotRestoredAsTheOriginalSetting() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(1))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("127.0.0.1:13360"))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        fixture.manager.release().getOrThrow()

        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable], "the dead proxy stays off")
        fixture.close()
    }

    @Test
    fun aLiveLoopbackProxyOfAnotherToolIsRestoredAsTheOriginalSetting() {
        val fixture = ProxyFixture()
        fixture.openPorts += 13_360
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(1))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("127.0.0.1:13360"))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()

        fixture.manager.release().getOrThrow()

        assertEquals(DesktopWindowsRegistryValue.dword(1), fixture.registry[ProxyEnable])
        assertEquals(DesktopWindowsRegistryValue.string("127.0.0.1:13360"), fixture.registry[ProxyServer])
        fixture.close()
    }

    @Test
    fun repairSwitchesOffAnEnabledProxyThatPointsAtADeadLoopbackPort() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(1))
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("http=127.0.0.1:13360;https=127.0.0.1:13360"))

        val repaired = fixture.manager.repairStale().getOrThrow()

        assertTrue(repaired)
        assertEquals(DesktopWindowsRegistryValue.dword(0), fixture.registry[ProxyEnable])
        assertEquals(
            DesktopWindowsRegistryValue.string("http=127.0.0.1:13360;https=127.0.0.1:13360"),
            fixture.registry[ProxyServer],
            "only the switch changes",
        )
        fixture.close()
    }

    @Test
    fun repairLeavesLiveRemoteAndOurOwnProxiesAlone() {
        val fixture = ProxyFixture()
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(1))

        fixture.openPorts += 13_360
        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("127.0.0.1:13360"))
        assertFalse(fixture.manager.repairStale().getOrThrow(), "a live loopback proxy")

        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("corp-proxy:8080"))
        assertFalse(fixture.manager.repairStale().getOrThrow(), "a remote proxy")

        fixture.registry.put(ProxyServer, DesktopWindowsRegistryValue.string("127.0.0.1:14000"))
        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(0))
        assertFalse(fixture.manager.repairStale().getOrThrow(), "a disabled proxy")

        fixture.registry.put(ProxyEnable, DesktopWindowsRegistryValue.dword(1))
        fixture.manager.acquire(DesktopWindowsHttpProxyEndpoint(port = 11_809)).getOrThrow()
        assertFalse(fixture.manager.repairStale().getOrThrow(), "the lease of this very session")
        assertEquals(DesktopWindowsRegistryValue.dword(1), fixture.registry[ProxyEnable])
        fixture.close()
    }

    private class ProxyFixture {
        val directory: Path = Files.createTempDirectory("skipi-system-proxy-test")
        val leasePath: Path = directory.resolve("system-proxy-lease.json")
        val registry = FakeRegistry()
        val commandRunner = FakeWindowsCommandRunner(registry)
        /** The loopback ports something listens on; every other loopback port is dead. */
        val openPorts = mutableSetOf<Int>()
        val manager = newManager()

        fun newManager(): DesktopWindowsSystemProxyLeaseManager = DesktopWindowsSystemProxyLeaseManager(
            leasePath = leasePath,
            commandRunner = commandRunner,
            loopbackPortOpen = { port -> port in openPorts },
        )

        fun close() {
            directory.toFile().deleteRecursively()
        }
    }

    private class FakeRegistry {
        private val values = mutableMapOf<Pair<String, String>, DesktopWindowsRegistryValue>()

        operator fun get(valueName: String): DesktopWindowsRegistryValue? = values[InternetSettingsKey to valueName]

        fun put(valueName: String, value: DesktopWindowsRegistryValue) {
            values[InternetSettingsKey to valueName] = value
        }

        fun marker(): String? = values[MarkerRegistryKey to MarkerRegistryValue]?.data

        fun deleteMarker() {
            values.remove(MarkerRegistryKey to MarkerRegistryValue)
        }

        fun read(key: String, name: String): DesktopWindowsRegistryValue? = values[key to name]

        fun write(key: String, name: String, value: DesktopWindowsRegistryValue) {
            values[key to name] = value
        }

        fun delete(key: String, name: String) {
            values.remove(key to name)
        }
    }

    private class FakeWindowsCommandRunner(
        private val registry: FakeRegistry,
    ) : DesktopWindowsCommandRunner {
        var refreshCalls = 0
            private set
        private var failedRegistryWrite: Pair<String, String>? = null
        private var failNextRefresh = false

        fun failNextRegistryWrite(key: String, name: String) {
            failedRegistryWrite = key to name
        }

        fun failNextRefresh() {
            failNextRefresh = true
        }

        override fun run(executable: String, arguments: List<String>): Result<DesktopWindowsCommandResult> = runCatching {
            when (executable.lowercase()) {
                "reg.exe" -> runRegistry(arguments)
                "powershell.exe" -> {
                    refreshCalls += 1
                    if (failNextRefresh) {
                        failNextRefresh = false
                        DesktopWindowsCommandResult(exitCode = 1, output = "refresh denied")
                    } else {
                        DesktopWindowsCommandResult(exitCode = 0)
                    }
                }

                else -> error("Unexpected executable: $executable")
            }
        }

        private fun runRegistry(arguments: List<String>): DesktopWindowsCommandResult {
            val operation = arguments.first().lowercase()
            val key = arguments[1]
            val valueName = arguments[arguments.indexOf("/v") + 1]
            return when (operation) {
                "query" -> registry.read(key, valueName)?.let { value ->
                    DesktopWindowsCommandResult(
                        exitCode = 0,
                        output = "    $valueName    ${value.type.regArgument}    ${value.data}",
                    )
                } ?: DesktopWindowsCommandResult(exitCode = 1)

                "add" -> {
                    if (failedRegistryWrite == key to valueName) {
                        failedRegistryWrite = null
                        return DesktopWindowsCommandResult(exitCode = 5, output = "access denied")
                    }
                    val type = DesktopWindowsRegistryValueType.entries.first { candidate ->
                        candidate.regArgument == arguments[arguments.indexOf("/t") + 1]
                    }
                    registry.write(key, valueName, DesktopWindowsRegistryValue(type, arguments[arguments.indexOf("/d") + 1]))
                    DesktopWindowsCommandResult(exitCode = 0)
                }

                "delete" -> {
                    registry.delete(key, valueName)
                    DesktopWindowsCommandResult(exitCode = 0)
                }

                else -> error("Unexpected reg.exe operation: $operation")
            }
        }
    }

    private companion object {
        const val InternetSettingsKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        const val MarkerRegistryKey = "HKCU\\Software\\SKIPI\\Desktop"
        const val MarkerRegistryValue = "SystemProxyLease"
        const val ProxyEnable = "ProxyEnable"
        const val ProxyServer = "ProxyServer"
        const val ProxyOverride = "ProxyOverride"
    }
}
