// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionAccessLogFeedTest {
    private fun reply(seq: Long, vararg hosts: String) =
        """{"seq":$seq,"entries":[${hosts.withIndex().joinToString(",") { (i, host) ->
            """{"seq":${seq - hosts.size + i + 1},"time":0,"host":"$host","port":443,"network":"tcp","outbound":"proxy"}"""
        }}]}"""

    @Test
    fun newConnectionsBecomePlainTextLines() {
        val repository = InMemoryCoreLogRepository()
        val replies = ArrayDeque(listOf(reply(2, "a.com", "b.com"), reply(3, "c.com")))
        val feed = ConnectionAccessLogFeed(repository, { Result.success(replies.removeFirst()) }, { "12:00:00" })

        feed.poll()
        feed.poll()

        assertEquals(
            listOf("12:00:00  tcp  a.com:443  → proxy", "12:00:00  tcp  b.com:443  → proxy", "12:00:00  tcp  c.com:443  → proxy"),
            repository.entries.value.map { it.message },
        )
    }

    @Test
    fun anEmptyPollLeavesTheEntriesAlone() {
        val repository = InMemoryCoreLogRepository()
        val feed = ConnectionAccessLogFeed(repository, { Result.success(reply(0)) }, { "12:00:00" })
        feed.poll()
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun resetStartsTheCursorOver() {
        val asked = mutableListOf<Long>()
        val repository = InMemoryCoreLogRepository()
        val feed = ConnectionAccessLogFeed(repository, { after -> asked += after; Result.success(reply(4, "a.com")) }, { "t" })
        feed.poll()
        feed.reset()
        feed.poll()
        assertEquals(listOf(0L, 0L), asked)
    }
}
