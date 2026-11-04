// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionLogStateTest {
    private fun entry(
        seq: Long,
        host: String = "example.test",
        outbound: String = "proxy",
        port: Int = 443,
        time: Long = seq * 1_000,
    ) = ConnectionLogEntry(seq = seq, timeMillis = time, host = host, port = port, network = "tcp", outbound = outbound)

    @Test
    fun newestConnectionComesFirst() {
        val state = ConnectionLogState().append(listOf(entry(1, "one.test"), entry(2, "two.test")))

        assertEquals(listOf("two.test", "one.test"), state.rows.map { it.host })
    }

    @Test
    fun repeatsOfTheNewestRowCollapseIntoACountWithTheLatestTime() {
        val state = ConnectionLogState()
            .append(listOf(entry(1), entry(2)))
            .append(listOf(entry(3)))

        val row = state.rows.single()
        assertEquals(3, row.count)
        assertEquals(3_000, row.timeMillis)
    }

    @Test
    fun theSameHostOnAnotherRouteOrAfterAnotherHostIsANewRow() {
        val state = ConnectionLogState().append(
            listOf(entry(1), entry(2, outbound = "direct"), entry(3, "other.test"), entry(4)),
        )

        assertEquals(4, state.rows.size)
        assertTrue(state.rows.all { it.count == 1 })
    }

    @Test
    fun aRepeatKeepsItsRowIdentitySoTheListDoesNotJump() {
        val first = ConnectionLogState().append(listOf(entry(1)))
        val repeated = first.append(listOf(entry(2)))

        assertEquals(first.rows.single().id, repeated.rows.single().id)
    }

    @Test
    fun onlyTheNewestRowsAreKept() {
        val entries = (1L..ConnectionLogState.Capacity + 5L).map { entry(it, "host$it.test") }

        val state = ConnectionLogState().append(entries)

        assertEquals(ConnectionLogState.Capacity, state.rows.size)
        assertEquals("host${ConnectionLogState.Capacity + 5}.test", state.rows.first().host)
    }

    @Test
    fun whileHeldTheListStaysStillAndCountsWhatArrived() {
        val before = ConnectionLogState().append(listOf(entry(1, "seen.test")))

        val held = before.hold().append(listOf(entry(2, "a.test"), entry(3, "b.test")))

        assertEquals(before.rows, held.rows)
        assertEquals(2, held.heldCount)
        assertTrue(held.held)
    }

    @Test
    fun releaseAddsEverythingThatArrivedWhileHeld() {
        val state = ConnectionLogState()
            .append(listOf(entry(1, "seen.test")))
            .hold()
            .append(listOf(entry(2, "a.test"), entry(3, "b.test")))
            .release()

        assertEquals(listOf("b.test", "a.test", "seen.test"), state.rows.map { it.host })
        assertEquals(0, state.heldCount)
        assertFalse(state.held)
    }

    @Test
    fun clearForgetsRowsAndHeldEntriesButKeepsTheHold() {
        val state = ConnectionLogState()
            .append(listOf(entry(1)))
            .hold()
            .append(listOf(entry(2, "a.test")))
            .clear()

        assertTrue(state.rows.isEmpty())
        assertEquals(0, state.heldCount)
        assertTrue(state.held)
    }

    @Test
    fun aDomainIsShownAloneAndAnAddressWithItsPort() {
        val rows = ConnectionLogState().append(
            listOf(
                entry(1, "example.test"),
                entry(2, "149.154.167.41"),
                entry(3, "2001:db8::1"),
            ),
        ).rows.reversed()

        assertEquals("example.test", rows[0].host)
        assertFalse(rows[0].isAddress)
        assertEquals("149.154.167.41:443", rows[1].host)
        assertTrue(rows[1].isAddress)
        assertEquals("[2001:db8::1]:443", rows[2].host)
        assertTrue(rows[2].isAddress)
    }

    @Test
    fun theRouteColourFollowsTheBuiltInTagsAndEveryOtherTagIsAProxy() {
        val rows = ConnectionLogState().append(
            listOf(
                entry(1, "a.test", "proxy"),
                entry(2, "b.test", "direct"),
                entry(3, "c.test", "block"),
                entry(4, "d.test", "proxy-2"),
            ),
        ).rows.reversed()

        assertEquals(
            listOf(
                ConnectionLogRoute.Proxy,
                ConnectionLogRoute.Direct,
                ConnectionLogRoute.Block,
                ConnectionLogRoute.Proxy,
            ),
            rows.map { it.route },
        )
        assertEquals("proxy-2", rows[3].outbound)
    }

    @Test
    fun readsTheCoreSnapshot() {
        val snapshot = parseConnectionLogSnapshot(
            """{"seq":7,"entries":[{"seq":7,"time":1000,"host":"a.test","port":443,"network":"tcp","outbound":"proxy"}]}""",
        )

        assertEquals(7, snapshot.seq)
        assertEquals(listOf(entry(7, "a.test", time = 1000)), snapshot.entries)
    }
}
