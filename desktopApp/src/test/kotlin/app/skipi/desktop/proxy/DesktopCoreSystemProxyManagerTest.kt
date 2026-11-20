// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The system proxy belongs to SKIPI Core on every desktop (see skipi-core's desktop/sysproxy); the host
 * only asks, and words what Core reports. Core is replaced here by a recording bridge.
 */
class DesktopCoreSystemProxyManagerTest {
    private val endpoints = DesktopSystemProxyEndpoints(httpPort = 10809, socksPort = 10808)

    private class Bridge(private val answers: MutableMap<String, String> = mutableMapOf()) : DesktopSystemProxyCore {
        val calls = mutableListOf<Pair<String, String?>>()
        var failure: String? = null

        fun answer(operation: String, json: String) {
            answers[operation] = json
        }

        override fun call(operation: String, arguments: String?): String {
            calls += operation to arguments
            failure?.let { throw IllegalStateException(it) }
            return answers[operation] ?: "{}"
        }
    }

    private fun manager(bridge: Bridge, os: String = "Windows 11") = DesktopCoreSystemProxyManager(
        core = { Result.success(bridge) },
        directory = Path.of("lease-dir"),
        osName = os,
    )

    @Test
    fun theCoreIsToldWhereToKeepItsLeaseOnceBeforeTheFirstOperation() {
        val bridge = Bridge().apply { answer("acquire", """{"action":"acquired"}""") }
        val manager = manager(bridge)

        manager.acquire(endpoints).getOrThrow()
        manager.acquire(endpoints).getOrThrow()

        assertEquals(listOf("configure", "acquire", "acquire"), bridge.calls.map { it.first })
        assertTrue(bridge.calls.first().second!!.contains("lease-dir"))
    }

    @Test
    fun acquireHandsTheLoopbackEndpointsToTheCore() {
        val bridge = Bridge().apply { answer("acquire", """{"action":"acquired"}""") }

        val result = manager(bridge).acquire(endpoints).getOrThrow()

        assertEquals(DesktopSystemProxyLeaseAction.Acquired, result.action)
        val arguments = bridge.calls.last { it.first == "acquire" }.second!!
        assertTrue(arguments.contains(""""host":"127.0.0.1"""") && arguments.contains(""""httpPort":10809""") && arguments.contains(""""socksPort":10808"""))
    }

    @Test
    fun everyActionTheCoreReportsIsMappedAndWorded() {
        val expected = mapOf(
            "acquired" to DesktopSystemProxyLeaseAction.Acquired,
            "alreadyAcquired" to DesktopSystemProxyLeaseAction.AlreadyAcquired,
            "released" to DesktopSystemProxyLeaseAction.Released,
            "nothingToRelease" to DesktopSystemProxyLeaseAction.NothingToRelease,
            "recovered" to DesktopSystemProxyLeaseAction.Recovered,
            "skippedNotOwner" to DesktopSystemProxyLeaseAction.SkippedNotOwner,
        )
        for ((wire, action) in expected) {
            val bridge = Bridge().apply {
                answer("acquire", """{"action":"$wire"}""")
                answer("release", """{"action":"$wire"}""")
                answer("recover", """{"action":"$wire"}""")
            }
            for (os in listOf("Windows 11", "Linux", "Mac OS X")) {
                val manager = manager(bridge, os)
                val results = listOf(manager.acquire(endpoints), manager.release(), manager.recover()).map { it.getOrThrow() }
                results.forEach { result ->
                    assertEquals(action, result.action, "$wire on $os")
                    assertTrue(result.message.isNotBlank(), "$wire on $os needs a message for the status line")
                }
            }
        }
    }

    @Test
    fun releaseAndRecoverAskTheCoreForTheirOwnOperation() {
        val bridge = Bridge().apply {
            answer("release", """{"action":"released"}""")
            answer("recover", """{"action":"recovered"}""")
        }
        val manager = manager(bridge)

        manager.release().getOrThrow()
        manager.recover().getOrThrow()

        assertEquals(listOf("configure", "release", "recover"), bridge.calls.map { it.first })
    }

    @Test
    fun repairStaleReportsWhetherTheCoreChangedSomething() {
        val bridge = Bridge().apply { answer("repairStale", """{"changed":true}""") }
        assertTrue(manager(bridge).repairStale().getOrThrow())
        bridge.answer("repairStale", """{"changed":false}""")
        assertFalse(manager(bridge).repairStale().getOrThrow())
    }

    @Test
    fun forceClearAsksTheCoreAndReportsItsFailure() {
        val bridge = Bridge()
        assertTrue(manager(bridge).forceClear().isSuccess)
        assertEquals("forceClear", bridge.calls.last().first)

        bridge.failure = "ProxyEnable stayed 1"
        val failed = manager(bridge).forceClear()
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("ProxyEnable stayed 1"))
    }

    @Test
    fun aCoreFailureOrAnUnloadableCoreBecomesAFailedResultNotACrash() {
        val bridge = Bridge().apply { failure = "access denied" }
        assertTrue(manager(bridge).acquire(endpoints).isFailure)

        val unloadable = DesktopCoreSystemProxyManager(
            core = { Result.failure(IllegalStateException("no core library")) },
            directory = Path.of("lease-dir"),
            osName = "Linux",
        )
        assertTrue(unloadable.acquire(endpoints).isFailure)
        assertFalse(unloadable.isSupportedHost())
    }

    @Test
    fun windowsIsSupportedWithoutLoadingTheCoreButOtherSystemsAskIt() {
        val bridge = Bridge().apply { answer("supported", """{"supported":false}""") }

        assertTrue(manager(bridge, "Windows 11").isSupportedHost())
        assertTrue(bridge.calls.isEmpty(), "Windows needs nothing installed, so the UI must not wait for Core")

        assertFalse(manager(bridge, "Linux").isSupportedHost())
        bridge.answer("supported", """{"supported":true}""")
        assertTrue(manager(bridge, "Mac OS X").isSupportedHost())
    }

    @Test
    fun theFactoryPicksNoProxyOnAnUnknownSystem() {
        val manager = DesktopSystemProxyManagers.create(core = { Result.success(Bridge()) }, osName = "FreeBSD")
        assertFalse(manager.isSupportedHost())
    }
}
