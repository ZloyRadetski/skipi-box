// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopTeardownHookTest {
    private val calls = mutableListOf<String>()

    private fun hook(work: Boolean = true, timeoutMillis: Long = 2_000, proxy: () -> Unit = { calls += "proxy" }) = DesktopTeardownHook(
        hasWorkToDo = { work },
        releaseSystemProxy = proxy,
        stopTun = { calls += "tun" },
        stopCore = { calls += "core" },
        timeoutMillis = timeoutMillis,
    )

    @Test
    fun releasesTheProxyFirstThenTheTunThenCore() {
        hook().run()

        assertEquals(listOf("proxy", "tun", "core"), calls)
    }

    @Test
    fun runsOnlyOnce() {
        val hook = hook()
        hook.run()
        hook.run()

        assertEquals(3, calls.size)
    }

    @Test
    fun doesNothingWhenCoreWasNeverLoaded() {
        hook(work = false).run()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun aFailingStepDoesNotSkipTheOthers() {
        hook(proxy = { error("boom") }).run()

        assertEquals(listOf("tun", "core"), calls)
    }

    @Test
    fun aHangingStepDoesNotHoldTheProcessPastTheTimeout() {
        val started = System.nanoTime()
        hook(timeoutMillis = 200, proxy = { Thread.sleep(60_000) }).run()

        assert((System.nanoTime() - started) / 1_000_000 < 5_000)
    }
}
