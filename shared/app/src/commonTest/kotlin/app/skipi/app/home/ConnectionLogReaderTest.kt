// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionLogReaderTest {
    private fun json(seq: Long, vararg entries: Triple<Long, String, String>) =
        """{"seq":$seq,"entries":[${entries.joinToString(",") { (s, host, out) ->
            """{"seq":$s,"time":1000,"host":"$host","port":443,"network":"tcp","outbound":"$out"}"""
        }}]}"""

    @Test
    fun readsOnlyWhatIsNewAndAdvancesTheCursor() {
        val asked = mutableListOf<Long>()
        val replies = ArrayDeque(
            listOf(
                json(2, Triple(1, "a.com", "proxy"), Triple(2, "b.com", "direct")),
                json(3, Triple(3, "c.com", "block")),
            ),
        )
        val reader = ConnectionLogReader { after -> asked += after; Result.success(replies.removeFirst()) }

        assertEquals(listOf("a.com", "b.com"), reader.next().map { it.host })
        assertEquals(listOf("c.com"), reader.next().map { it.host })
        assertEquals(listOf(0L, 2L), asked)
    }

    @Test
    fun aSmallerSeqMeansANewCoreSessionAndStartsOver() {
        val replies = ArrayDeque(listOf(json(5, Triple(5, "a.com", "proxy")), json(1), json(2, Triple(1, "n.com", "proxy"))))
        val asked = mutableListOf<Long>()
        val reader = ConnectionLogReader { after -> asked += after; Result.success(replies.removeFirst()) }

        reader.next()
        assertTrue(reader.next().isEmpty())
        assertEquals(listOf("n.com"), reader.next().map { it.host })
        assertEquals(listOf(0L, 5L, 0L), asked)
    }

    @Test
    fun aFailedOrBrokenReadIsEmptyAndKeepsTheCursor() {
        val replies = ArrayDeque(
            listOf(Result.success(json(1, Triple(1, "a.com", "proxy"))), Result.failure(IllegalStateException()), Result.success("not json")),
        )
        val asked = mutableListOf<Long>()
        val reader = ConnectionLogReader { after -> asked += after; replies.removeFirst() }

        reader.next()
        assertTrue(reader.next().isEmpty())
        assertTrue(reader.next().isEmpty())
        assertEquals(listOf(0L, 1L, 1L), asked)
    }

    @Test
    fun resetForgetsTheCursor() {
        val asked = mutableListOf<Long>()
        val reader = ConnectionLogReader { after -> asked += after; Result.success(json(4, Triple(4, "a.com", "proxy"))) }
        reader.next()
        reader.reset()
        reader.next()
        assertEquals(listOf(0L, 0L), asked)
    }

    @Test
    fun accessLineIsPlainText() {
        val entry = ConnectionLogEntry(1, 0, "youtube.com", 443, "tcp", "proxy")
        assertEquals("12:03:41  tcp  youtube.com:443  → proxy", entry.accessLogText { "12:03:41" })
    }

    @Test
    fun ipv6HostGetsBrackets() {
        val entry = ConnectionLogEntry(1, 0, "2001:db8::1", 53, "udp", "direct")
        assertEquals("12:03:41  udp  [2001:db8::1]:53  → direct", entry.accessLogText { "12:03:41" })
    }

    @Test
    fun entriesCarryTheNameOfTheirOutbound() {
        val labels = OutboundLabels(mapOf(20 to "Frankfurt"), selectedName = null)
        val reader = ConnectionLogReader(labels = { labels }) { Result.success(json(1, Triple(1, "a.com", "20"))) }
        val entry = reader.next().single()
        assertEquals("20", entry.outbound)
        assertEquals("Frankfurt", entry.outboundLabel)
        assertEquals("12:03:41  tcp  a.com:443  → Frankfurt", entry.accessLogText { "12:03:41" })
    }
}
