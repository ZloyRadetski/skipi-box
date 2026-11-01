// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMacSystemProxyTest {
    private val endpoints = DesktopSystemProxyEndpoints(httpPort = 10809, socksPort = 10808)

    // ---- parsing of networksetup output --------------------------------------------------------

    @Test
    fun theServiceListSkipsTheNoticeAndMarksDisabledServices() {
        val output = """
            An asterisk (*) denotes that a network service is disabled.
            Wi-Fi
            *Thunderbolt Bridge
            USB 10/100/1000 LAN
        """.trimIndent()

        assertEquals(
            listOf(
                MacNetworkService("Wi-Fi", enabled = true),
                MacNetworkService("Thunderbolt Bridge", enabled = false),
                MacNetworkService("USB 10/100/1000 LAN", enabled = true),
            ),
            parseMacNetworkServices(output),
        )
    }

    @Test
    fun anEmptyServiceListIsEmpty() {
        assertEquals(emptyList(), parseMacNetworkServices(""))
        assertEquals(emptyList(), parseMacNetworkServices("An asterisk (*) denotes that a network service is disabled.\n"))
    }

    @Test
    fun aProxyStateIsReadFromItsThreeFields() {
        val on = parseMacProxyState("Enabled: Yes\nServer: 127.0.0.1\nPort: 10809\nAuthenticated Proxy Enabled: 0\n")
        assertEquals(MacProxyState(enabled = true, host = "127.0.0.1", port = 10809), on)

        val off = parseMacProxyState("Enabled: No\nServer: \nPort: 0\nAuthenticated Proxy Enabled: 0\n")
        assertEquals(MacProxyState(enabled = false, host = "", port = 0), off)

        assertNull(parseMacProxyState("not a proxy report"))
    }

    @Test
    fun bypassDomainsAreOneLinePerEntryAndEmptyWhenNoneAreSet() {
        assertEquals(
            listOf("*.local", "169.254/16"),
            parseMacBypassDomains("*.local\n169.254/16\n"),
        )
        assertEquals(emptyList(), parseMacBypassDomains("There aren't any bypass domains set on this service.\n"))
        assertEquals(emptyList(), parseMacBypassDomains(""))
    }

    @Test
    fun bypassDomainsKeepTheUsersEntriesAndAlwaysAddLoopback() {
        val merged = mergeMacBypassDomains(listOf("*.local", "corp.example", "LOCALHOST"))
        assertEquals(listOf("*.local", "corp.example", "LOCALHOST", "127.0.0.1", "::1"), merged)
        assertEquals(merged.size, merged.map(String::lowercase).distinct().size)
    }

    // ---- the lease manager against a fake networksetup -----------------------------------------

    @Test
    fun acquireSwitchesOnEveryEnabledServiceAndSkipsDisabledOnes() {
        val fixture = MacProxyFixture()
        val acquired = fixture.manager.acquire(endpoints).getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Acquired, acquired.action)
        listOf("Wi-Fi", "USB 10/100/1000 LAN").forEach { service ->
            val state = fixture.services.getValue(service)
            assertEquals(MacProxyState(true, "127.0.0.1", 10809), state.web, service)
            assertEquals(MacProxyState(true, "127.0.0.1", 10809), state.secureWeb, service)
            assertEquals(MacProxyState(true, "127.0.0.1", 10808), state.socks, service)
        }
        assertEquals(MacProxyState(false, "", 0), fixture.services.getValue("Thunderbolt Bridge").web, "a disabled service is left alone")
        assertTrue(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun releaseRestoresEachServicesOwnSettingsExactly() {
        val fixture = MacProxyFixture()
        fixture.services.getValue("Wi-Fi").apply {
            web = MacProxyState(true, "proxy.corp", 3128)
            secureWeb = MacProxyState(true, "proxy.corp", 3129)
            socks = MacProxyState(false, "old.socks", 1081)
            bypass = mutableListOf("*.local", "corp.example")
        }
        val before = fixture.snapshot()

        fixture.manager.acquire(endpoints).getOrThrow()
        assertTrue("corp.example" in fixture.services.getValue("Wi-Fi").bypass, "the user's bypass entries stay while the proxy is on")
        assertTrue("127.0.0.1" in fixture.services.getValue("Wi-Fi").bypass && "::1" in fixture.services.getValue("Wi-Fi").bypass)

        val released = fixture.manager.release().getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Released, released.action)
        assertEquals(before, fixture.snapshot())
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun acquiringTwiceWithTheSameEndpointsChangesNothing() {
        val fixture = MacProxyFixture()
        fixture.manager.acquire(endpoints).getOrThrow()
        val second = fixture.manager.acquire(endpoints).getOrThrow()
        assertEquals(DesktopSystemProxyLeaseAction.AlreadyAcquired, second.action)
        fixture.close()
    }

    @Test
    fun anInterruptedSessionIsRecoveredFromTheLease() {
        val fixture = MacProxyFixture()
        val before = fixture.snapshot()
        fixture.manager.acquire(endpoints).getOrThrow()

        val recovered = fixture.newManager().recover().getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Recovered, recovered.action)
        assertEquals(before, fixture.snapshot())
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun recoverWithoutALeaseDoesNothing() {
        val fixture = MacProxyFixture()
        assertEquals(DesktopSystemProxyLeaseAction.NothingToRelease, fixture.manager.recover().getOrThrow().action)
        assertEquals(DesktopSystemProxyLeaseAction.NothingToRelease, fixture.manager.release().getOrThrow().action)
        fixture.close()
    }

    @Test
    fun forceClearSwitchesTheProxiesOffAndDropsTheLease() {
        val fixture = MacProxyFixture()
        fixture.manager.acquire(endpoints).getOrThrow()

        fixture.manager.forceClear().getOrThrow()

        listOf("Wi-Fi", "USB 10/100/1000 LAN").forEach { service ->
            val state = fixture.services.getValue(service)
            assertFalse(state.web.enabled, service)
            assertFalse(state.secureWeb.enabled, service)
            assertFalse(state.socks.enabled, service)
        }
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun aServiceThatCannotBeReadDoesNotStopTheOthers() {
        val fixture = MacProxyFixture()
        fixture.unreadable += "USB 10/100/1000 LAN"

        fixture.manager.acquire(endpoints).getOrThrow()

        assertTrue(fixture.services.getValue("Wi-Fi").web.enabled)
        assertFalse(fixture.services.getValue("USB 10/100/1000 LAN").web.enabled)
        fixture.close()
    }

    @Test
    fun aServiceThatVanishedMeanwhileDoesNotFailTheRestore() {
        val fixture = MacProxyFixture()
        fixture.manager.acquire(endpoints).getOrThrow()
        fixture.services.remove("USB 10/100/1000 LAN")

        val released = fixture.manager.release().getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Released, released.action)
        assertFalse(fixture.services.getValue("Wi-Fi").web.enabled)
        fixture.close()
    }

    @Test
    fun theHostIsSupportedOnlyWhenNetworksetupAnswers() {
        val working = MacProxyFixture()
        assertTrue(working.manager.isSupportedHost())
        working.close()

        val missing = MacProxyFixture()
        missing.networksetupAvailable = false
        assertFalse(missing.manager.isSupportedHost())
        missing.close()
    }

    @Test
    fun theFactoryPicksTheMacManagerOnMacOs() {
        assertTrue(DesktopSystemProxyManagers.create("Mac OS X") is DesktopMacSystemProxyLeaseManager)
        assertTrue(isDesktopSystemProxySupported("Mac OS X"))
        assertTrue(isDesktopSystemProxySupported("macOS"))
    }

    // ---- fixture ---------------------------------------------------------------------------------

    private class ServiceSettings(
        var web: MacProxyState = MacProxyState(false, "", 0),
        var secureWeb: MacProxyState = MacProxyState(false, "", 0),
        var socks: MacProxyState = MacProxyState(false, "", 0),
        var bypass: MutableList<String> = mutableListOf("*.local", "169.254/16"),
    ) {
        fun copyOf() = ServiceSettings(web, secureWeb, socks, bypass.toMutableList())
        override fun equals(other: Any?) = other is ServiceSettings &&
            web == other.web && secureWeb == other.secureWeb && socks == other.socks && bypass == other.bypass
        override fun hashCode() = listOf(web, secureWeb, socks, bypass).hashCode()
        override fun toString() = "ServiceSettings($web, $secureWeb, $socks, $bypass)"
    }

    private class MacProxyFixture {
        val directory: Path = Files.createTempDirectory("skipi-mac-proxy")
        val leasePath: Path = directory.resolve("mac-system-proxy-lease.json")
        val services = linkedMapOf(
            "Wi-Fi" to ServiceSettings(),
            "Thunderbolt Bridge" to ServiceSettings(),
            "USB 10/100/1000 LAN" to ServiceSettings(),
        )
        val disabled = mutableSetOf("Thunderbolt Bridge")
        val unreadable = mutableSetOf<String>()
        var networksetupAvailable = true

        val manager = newManager()

        fun newManager() = DesktopMacSystemProxyLeaseManager(leasePath, ::run)

        fun snapshot(): Map<String, ServiceSettings> = services.mapValues { (_, value) -> value.copyOf() }

        fun close() {
            directory.toFile().deleteRecursively()
        }

        private fun report(state: MacProxyState) =
            "Enabled: ${if (state.enabled) "Yes" else "No"}\nServer: ${state.host}\nPort: ${state.port}\nAuthenticated Proxy Enabled: 0\n"

        private fun run(executable: String, arguments: List<String>): Result<DesktopLinuxCommandResult> {
            if (executable != "networksetup" || !networksetupAvailable) {
                return Result.success(DesktopLinuxCommandResult(127, "command not found"))
            }
            val command = arguments.firstOrNull().orEmpty()
            val service = arguments.getOrNull(1).orEmpty()
            val settings = services[service]
            fun ok(output: String = "") = Result.success(DesktopLinuxCommandResult(0, output))
            fun failure(message: String) = Result.success(DesktopLinuxCommandResult(1, message))
            if (command == "-listallnetworkservices") {
                val listing = services.keys.joinToString("\n") { name -> if (name in disabled) "*$name" else name }
                return ok("An asterisk (*) denotes that a network service is disabled.\n$listing\n")
            }
            if (settings == null) return failure("** Error: $service is not a recognized network service.")
            if (service in unreadable && command.startsWith("-get")) return failure("** Error: cannot read $service")
            return when (command) {
                "-getwebproxy" -> ok(report(settings.web))
                "-getsecurewebproxy" -> ok(report(settings.secureWeb))
                "-getsocksfirewallproxy" -> ok(report(settings.socks))
                "-getproxybypassdomains" -> ok(
                    if (settings.bypass.isEmpty()) "There aren't any bypass domains set on this service.\n"
                    else settings.bypass.joinToString("\n", postfix = "\n"),
                )
                "-setwebproxy" -> { settings.web = MacProxyState(true, arguments[2], arguments[3].toInt()); ok() }
                "-setsecurewebproxy" -> { settings.secureWeb = MacProxyState(true, arguments[2], arguments[3].toInt()); ok() }
                "-setsocksfirewallproxy" -> { settings.socks = MacProxyState(true, arguments[2], arguments[3].toInt()); ok() }
                "-setwebproxystate" -> { settings.web = settings.web.copy(enabled = arguments[2] == "on"); ok() }
                "-setsecurewebproxystate" -> { settings.secureWeb = settings.secureWeb.copy(enabled = arguments[2] == "on"); ok() }
                "-setsocksfirewallproxystate" -> { settings.socks = settings.socks.copy(enabled = arguments[2] == "on"); ok() }
                "-setproxybypassdomains" -> {
                    val values = arguments.drop(2)
                    settings.bypass = if (values == listOf("Empty")) mutableListOf() else values.toMutableList()
                    ok()
                }
                else -> failure("unknown command $command")
            }
        }
    }
}
