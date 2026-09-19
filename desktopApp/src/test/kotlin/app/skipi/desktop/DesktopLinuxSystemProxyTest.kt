// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLinuxSystemProxyTest {

    @Test
    fun acquires_and_releases_exact_proxy_snapshot() {
        val fixture = LinuxProxyFixture()
        fixture.gsettings["org.gnome.system.proxy mode"] = "none"

        val endpoints = DesktopSystemProxyEndpoints(httpPort = 10809, socksPort = 10808)
        val acquired = fixture.manager.acquire(endpoints).getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Acquired, acquired.action)
        assertEquals("manual", fixture.gsettings["org.gnome.system.proxy mode"])
        assertEquals("'127.0.0.1'", fixture.gsettings["org.gnome.system.proxy.http host"])
        assertEquals("10809", fixture.gsettings["org.gnome.system.proxy.http port"])
        assertEquals("true", fixture.gsettings["org.gnome.system.proxy.http enabled"])
        assertEquals("'127.0.0.1'", fixture.gsettings["org.gnome.system.proxy.https host"])
        assertEquals("10809", fixture.gsettings["org.gnome.system.proxy.https port"])
        assertEquals("'127.0.0.1'", fixture.gsettings["org.gnome.system.proxy.socks host"])
        assertEquals("10808", fixture.gsettings["org.gnome.system.proxy.socks port"])
        assertTrue(Files.exists(fixture.leasePath))

        val released = fixture.manager.release().getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Released, released.action)
        assertEquals("none", fixture.gsettings["org.gnome.system.proxy mode"])
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun already_acquired_returns_idempotent_result() {
        val fixture = LinuxProxyFixture()
        fixture.gsettings["org.gnome.system.proxy mode"] = "none"
        val endpoints = DesktopSystemProxyEndpoints(httpPort = 10809, socksPort = 10808)

        fixture.manager.acquire(endpoints).getOrThrow()
        val second = fixture.manager.acquire(endpoints).getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.AlreadyAcquired, second.action)
        fixture.close()
    }

    @Test
    fun recover_restores_interrupted_lease() {
        val fixture = LinuxProxyFixture()
        fixture.gsettings["org.gnome.system.proxy mode"] = "none"
        val endpoints = DesktopSystemProxyEndpoints(httpPort = 10809, socksPort = 10808)

        fixture.manager.acquire(endpoints).getOrThrow()
        val restartedManager = fixture.newManager()
        val recovered = restartedManager.recover().getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Recovered, recovered.action)
        assertEquals("none", fixture.gsettings["org.gnome.system.proxy mode"])
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    @Test
    fun forceClear_resets_proxy_and_deletes_lease() {
        val fixture = LinuxProxyFixture()
        fixture.gsettings["org.gnome.system.proxy mode"] = "none"
        val endpoints = DesktopSystemProxyEndpoints(httpPort = 10809, socksPort = 10808)

        fixture.manager.acquire(endpoints).getOrThrow()
        assertEquals("manual", fixture.gsettings["org.gnome.system.proxy mode"])

        fixture.manager.forceClear().getOrThrow()
        assertEquals("none", fixture.gsettings["org.gnome.system.proxy mode"])
        assertFalse(Files.exists(fixture.leasePath))
        fixture.close()
    }

    private class LinuxProxyFixture : AutoCloseable {
        val temporaryDirectory: Path = Files.createTempDirectory("skipi-linux-proxy-test")
        val leasePath: Path = temporaryDirectory.resolve("linux-system-proxy-lease.json")
        val gsettings = mutableMapOf<String, String>()
        val kde = mutableMapOf<String, String>()

        val runner = DesktopLinuxCommandRunner { executable, arguments ->
            runCatching {
                when (executable) {
                    "which" -> {
                        val binary = arguments.firstOrNull()
                        if (binary == "gsettings") DesktopLinuxCommandResult(exitCode = 0, output = "/usr/bin/gsettings\n")
                        else DesktopLinuxCommandResult(exitCode = 1, output = "")
                    }
                    "gsettings" -> {
                        val op = arguments.getOrNull(0)
                        when (op) {
                            "get" -> {
                                val schema = arguments.getOrNull(1).orEmpty()
                                val key = arguments.getOrNull(2).orEmpty()
                                val fullKey = "$schema $key"
                                val value = gsettings[fullKey] ?: if (key == "mode") "'none'" else "''"
                                val formatted = if (value.startsWith("'") || value.startsWith("[") || value.toIntOrNull() != null || value == "true" || value == "false") {
                                    value
                                } else {
                                    "'$value'"
                                }
                                DesktopLinuxCommandResult(exitCode = 0, output = formatted)
                            }
                            "set" -> {
                                val schema = arguments.getOrNull(1).orEmpty()
                                val key = arguments.getOrNull(2).orEmpty()
                                val value = arguments.getOrNull(3).orEmpty()
                                gsettings["$schema $key"] = value
                                DesktopLinuxCommandResult(exitCode = 0, output = "")
                            }
                            else -> DesktopLinuxCommandResult(exitCode = 1, output = "unknown op")
                        }
                    }
                    else -> DesktopLinuxCommandResult(exitCode = 1, output = "unknown executable")
                }
            }
        }

        val manager = DesktopLinuxSystemProxyLeaseManager(
            leasePath = leasePath,
            commandRunner = runner,
        )

        fun newManager(): DesktopLinuxSystemProxyLeaseManager = DesktopLinuxSystemProxyLeaseManager(
            leasePath = leasePath,
            commandRunner = runner,
        )

        override fun close() {
            Files.walk(temporaryDirectory)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
