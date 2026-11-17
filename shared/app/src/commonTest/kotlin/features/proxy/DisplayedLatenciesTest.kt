// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy

import app.ProxyServerLatencyTesting
import app.ProxyServerState
import features.subscription.DefaultSubscriptionGroupId
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayedLatenciesTest {
    private fun server(id: Int, latency: String = "") = ProxyServerState(
        id = id,
        server = VLESS(remarks = "s$id", id = "u", server = "s$id.example", port = "443"),
        groupId = DefaultSubscriptionGroupId,
        latency = latency,
    )

    @Test
    fun theShownPingsComeBackAfterARestart() {
        val restored = DisplayedLatencies.restore(listOf(server(1), server(2)), mapOf(1 to "120", 2 to "-1"))

        assertEquals(listOf("120", "-1"), restored.map { it.latency })
    }

    @Test
    fun aPingMeasuredSinceTheStartIsNeverOverwrittenByASavedOne() {
        val restored = DisplayedLatencies.restore(listOf(server(1, latency = "45")), mapOf(1 to "120"))

        assertEquals("45", restored.single().latency)
    }

    @Test
    fun aServerThatWasNeverPingedStaysBlank() {
        assertEquals("", DisplayedLatencies.restore(listOf(server(7)), mapOf(1 to "120")).single().latency)
    }

    @Test
    fun theTestingMarkerIsNeverKept() {
        val kept = DisplayedLatencies.forPersistence(
            listOf(server(1, latency = ProxyServerLatencyTesting), server(2, latency = "80"), server(3)),
        )

        assertEquals(mapOf(2 to "80"), kept)
    }

    @Test
    fun theSavedTextRoundTripsAndGarbageMeansNothingSaved() {
        val map = mapOf(1 to "120", 5 to "-1")

        assertEquals(map, DisplayedLatencies.decode(DisplayedLatencies.encode(map)))
        assertEquals(emptyMap(), DisplayedLatencies.decode("not json"))
        assertEquals(emptyMap(), DisplayedLatencies.decode(null))
    }
}
