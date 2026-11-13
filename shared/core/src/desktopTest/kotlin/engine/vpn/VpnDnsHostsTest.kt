// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpnDnsHostsTest {
    /** Starts one blocking lookup per host from its own thread, as concurrent VPN starts would. */
    private fun hang(hosts: List<String>, release: CountDownLatch, entered: CountDownLatch): () -> Unit {
        val callers = Executors.newFixedThreadPool(hosts.size)
        hosts.forEach { host ->
            callers.execute {
                AppState().xrayDnsHosts(listOf(host)) {
                    entered.countDown()
                    // Like InetAddress.getAllByName: ignores interruption.
                    while (!release.await(30, TimeUnit.SECONDS)) Unit
                    emptyList()
                }
            }
        }
        return { callers.shutdownNow() }
    }

    @Test
    fun lookupsThatNeverFinishDoNotStarveLaterLookups() {
        val release = CountDownLatch(1)
        val hungHosts = (1..9).map { "hung$it.example.test" }
        val entered = CountDownLatch(hungHosts.size)
        val stop = hang(hungHosts, release, entered)
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "every hung lookup should get a thread")

            val result = AppState().xrayDnsHosts(listOf("fresh.example.test")) { listOf("1.2.3.4") }

            assertEquals(listOf("fresh.example.test:1.2.3.4"), result)
        } finally {
            release.countDown()
            stop()
        }
    }

    @Test
    fun whenTooManyLookupsHangANewOneIsSkippedPromptlyAndWorksAgainAfterTheyEnd() {
        val release = CountDownLatch(1)
        val hungHosts = (1..MaxConcurrentDnsLookups).map { "hung$it.example.test" }
        val entered = CountDownLatch(hungHosts.size)
        val stop = hang(hungHosts, release, entered)
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))

            val started = System.nanoTime()
            val skipped = AppState().xrayDnsHosts(listOf("extra.example.test")) { listOf("9.9.9.9") }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(emptyList(), skipped)
            assertTrue(elapsedMillis < 400, "a saturated pool must skip, not wait: $elapsedMillis ms")
        } finally {
            release.countDown()
            stop()
        }

        // Hung lookups return; the skipped one must not still be sitting in a queue, and new ones run.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var recovered = emptyList<String>()
        while (recovered.isEmpty() && System.nanoTime() < deadline) {
            recovered = AppState().xrayDnsHosts(listOf("later.example.test")) { listOf("5.6.7.8") }
        }
        assertEquals(listOf("later.example.test:5.6.7.8"), recovered)
    }
}
