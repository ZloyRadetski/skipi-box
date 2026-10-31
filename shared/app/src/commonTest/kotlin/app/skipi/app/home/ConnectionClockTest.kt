// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The moment a connection began has to outlive the screens that display it. */
class ConnectionClockTest {
    private val time = TestTimeSource()
    private val clock = ConnectionClock(time)

    @Test
    fun noConnectionHasNoStart() {
        assertNull(clock.update(connected = false))
        assertNull(clock.connectedSince)
    }

    @Test
    fun theStartIsTheMomentTheConnectionWasFirstSeen() {
        val first = assertNotNull(clock.update(connected = true))
        time += 90.seconds

        assertEquals(90.seconds, first.elapsedNow())
    }

    @Test
    fun seeingTheSameConnectionAgainKeepsItsStart() {
        val first = assertNotNull(clock.update(connected = true))
        time += 30.seconds

        val again = assertNotNull(clock.update(connected = true))
        time += 30.seconds

        assertEquals(60.seconds, again.elapsedNow())
        assertTrue(first === again, "the same mark, not a restarted one")
    }

    @Test
    fun aNewConnectionStartsAgainAfterADisconnect() {
        clock.update(connected = true)
        time += 120.seconds
        assertNull(clock.update(connected = false))
        time += 10.seconds

        val second = assertNotNull(clock.update(connected = true))
        time += 5.seconds

        assertEquals(5.seconds, second.elapsedNow())
    }
}
