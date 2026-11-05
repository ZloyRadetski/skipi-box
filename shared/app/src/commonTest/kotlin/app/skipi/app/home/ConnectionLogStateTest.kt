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

    private fun message(
        text: String,
        level: ConnectionLogLevel = ConnectionLogLevel.Error,
        source: ConnectionLogSource = ConnectionLogSource.App,
        time: Long = 1_000,
    ) = ConnectionLogMessage(timeMillis = time, level = level, source = source, text = text)

    private val ConnectionLogState.connections get() = rows.filterIsInstance<ConnectionLogRow>()

    private val ConnectionLogState.messages get() = rows.filterIsInstance<ConnectionLogMessageRow>()

    @Test
    fun newestConnectionComesFirst() {
        val state = ConnectionLogState().append(listOf(entry(1, "one.test"), entry(2, "two.test")))

        assertEquals(listOf("two.test", "one.test"), state.connections.map { it.host })
    }

    @Test
    fun repeatsOfTheNewestRowCollapseIntoACountWithTheLatestTime() {
        val state = ConnectionLogState()
            .append(listOf(entry(1), entry(2)))
            .append(listOf(entry(3)))

        val row = state.connections.single()
        assertEquals(3, row.count)
        assertEquals(3_000, row.timeMillis)
    }

    @Test
    fun theSameHostOnAnotherRouteOrAfterAnotherHostIsANewRow() {
        val state = ConnectionLogState().append(
            listOf(entry(1), entry(2, outbound = "direct"), entry(3, "other.test"), entry(4)),
        )

        assertEquals(4, state.rows.size)
        assertTrue(state.connections.all { it.count == 1 })
    }

    @Test
    fun aRepeatKeepsItsRowIdentitySoTheListDoesNotJump() {
        val first = ConnectionLogState().append(listOf(entry(1)))
        val repeated = first.append(listOf(entry(2)))

        assertEquals(first.connections.single().id, repeated.connections.single().id)
    }

    @Test
    fun onlyTheNewestRowsAreKept() {
        val entries = (1L..ConnectionLogState.Capacity + 5L).map { entry(it, "host$it.test") }

        val state = ConnectionLogState().append(entries)

        assertEquals(ConnectionLogState.Capacity, state.rows.size)
        assertEquals("host${ConnectionLogState.Capacity + 5}.test", state.connections.first().host)
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

        assertEquals(listOf("b.test", "a.test", "seen.test"), state.connections.map { it.host })
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
        ).connections.reversed()

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
        ).connections.reversed()

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

    @Test
    fun messagesAndConnectionsShareOneListInTheOrderTheyArrived() {
        val state = ConnectionLogState()
            .append(listOf(entry(1, "one.test"), message("Connection failed"), entry(2, "two.test")))

        assertEquals(
            listOf(ConnectionLogRow::class, ConnectionLogMessageRow::class, ConnectionLogRow::class),
            state.rows.map { it::class },
        )
        assertEquals("two.test", state.connections.first().host)
    }

    @Test
    fun aMessageShowsItsFirstLineAndCopiesAllOfIt() {
        val text = "Tunnel: Connection failed: refused\njava.io.IOException: refused\n\tat Tunnel.start"

        val row = ConnectionLogState().append(listOf(message(text))).messages.single()

        assertEquals("Tunnel: Connection failed: refused", row.title)
        assertEquals(text, row.copyText)
    }

    @Test
    fun aMessageCarriesItsLevelAndSource() {
        val row = ConnectionLogState()
            .append(listOf(message("slow", ConnectionLogLevel.Warning, ConnectionLogSource.Core)))
            .messages.single()

        assertEquals(ConnectionLogLevel.Warning, row.level)
        assertEquals(ConnectionLogSource.Core, row.source)
    }

    @Test
    fun repeatsOfTheNewestMessageCollapseIntoACountWithTheLatestTime() {
        val state = ConnectionLogState()
            .append(listOf(message("same", time = 1_000), message("same", time = 2_000)))

        val row = state.messages.single()
        assertEquals(2, row.count)
        assertEquals(2_000, row.timeMillis)
    }

    @Test
    fun theSameTextAtAnotherLevelOrFromAnotherSourceIsANewRow() {
        val state = ConnectionLogState().append(
            listOf(
                message("same"),
                message("same", ConnectionLogLevel.Warning),
                message("same", ConnectionLogLevel.Warning, ConnectionLogSource.Core),
            ),
        )

        assertEquals(3, state.rows.size)
    }

    @Test
    fun aMessageNeverCollapsesIntoAConnection() {
        val state = ConnectionLogState().append(listOf(entry(1, "same.test"), message("same.test")))

        assertEquals(2, state.rows.size)
    }

    @Test
    fun messagesArrivingWhileHeldJoinOnRelease() {
        val held = ConnectionLogState()
            .append(listOf(entry(1, "seen.test")))
            .hold()
            .append(listOf(message("Connection failed")))

        assertEquals(1, held.rows.size)
        assertEquals(1, held.heldCount)
        assertEquals("Connection failed", held.release().messages.single().title)
    }

    @Test
    fun rowsOfTwoCoreSessionsWithTheSameSequenceNumbersKeepDistinctIds() {
        val state = ConnectionLogState()
            .append(listOf(entry(1, "first.test"), entry(2, "second.test")))
            .append(listOf(entry(1, "third.test"), entry(2, "fourth.test")))

        assertEquals(4, state.rows.map { it.id }.toSet().size)
    }

    @Test
    fun idsStayUniqueAfterAClear() {
        val before = ConnectionLogState().append(listOf(entry(1, "first.test")))

        val after = before.clear().append(listOf(entry(1, "second.test")))

        assertTrue(before.rows.single().id != after.rows.single().id)
    }

    @Test
    fun levelNamesOfBothLogsAreUnderstoodAndAnythingElseIsInfo() {
        assertEquals(ConnectionLogLevel.Debug, connectionLogLevel("DEBUG"))
        assertEquals(ConnectionLogLevel.Info, connectionLogLevel("Info"))
        assertEquals(ConnectionLogLevel.Warning, connectionLogLevel("WARN"))
        assertEquals(ConnectionLogLevel.Warning, connectionLogLevel("Warning"))
        assertEquals(ConnectionLogLevel.Error, connectionLogLevel("error"))
        assertEquals(ConnectionLogLevel.Info, connectionLogLevel("other"))
    }
}
