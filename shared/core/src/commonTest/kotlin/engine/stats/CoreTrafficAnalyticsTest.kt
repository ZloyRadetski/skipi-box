// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import engine.xray.XrayTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreTrafficAnalyticsTest {
    @Test
    fun parsesAndAggregatesCoreTrafficStats() {
        val inbound = requireNotNull(
            parseXrayInboundTrafficStat(
                name = "inbound>>>socks-in>>>traffic>>>uplink",
                bytes = 100L,
            ),
        )
        val outbound = requireNotNull(
            parseXrayOutboundTrafficStat(
                name = "outbound>>>proxy-main>>>traffic>>>downlink",
                bytes = 300L,
            ),
        )

        assertEquals(
            XrayTrafficStat(
                tag = "socks-in",
                direction = XrayTrafficDirection.Uplink,
                bytes = 100L,
            ),
            inbound,
        )
        assertEquals(
            mapOf("proxy-main" to XrayTrafficBytes(downlink = 300L)),
            aggregateOutboundTraffic(listOf(outbound)),
        )
        assertNull(parseXrayInboundTrafficStat("inbound>>>socks-in>>>traffic>>>invalid", 1L))
        assertNull(parseXrayOutboundTrafficStat("outbound>>>proxy-main>>>traffic", 1L))
    }

    @Test
    fun retainsActiveOutboundUntilAnotherCarriesTwiceItsNewTraffic() {
        val previous = mapOf(
            "proxy-primary" to XrayTrafficBytes(),
            "proxy-secondary" to XrayTrafficBytes(),
        )

        assertEquals(
            "proxy-primary",
            mapOf(
                "proxy-primary" to XrayTrafficBytes(uplink = 2_048L),
                "proxy-secondary" to XrayTrafficBytes(uplink = 3_000L),
            ).maxTrafficDeltaComparedTo(
                previous = previous,
                currentActiveTag = "proxy-primary",
            ),
        )
        assertEquals(
            "proxy-secondary",
            mapOf(
                "proxy-primary" to XrayTrafficBytes(uplink = 2_048L),
                "proxy-secondary" to XrayTrafficBytes(uplink = 5_000L),
            ).maxTrafficDeltaComparedTo(
                previous = previous,
                currentActiveTag = "proxy-primary",
            ),
        )
    }

    @Test
    fun accumulatesTrafficAndUsesAThreeSecondSpeedWindow() {
        val accumulator = XrayTrafficSessionAccumulator()

        val first = accumulator.record(
            delta = XrayTrafficBytes(uplink = 1_000L, downlink = 500L),
            elapsedMillis = 1_000L,
        )
        val second = accumulator.record(
            delta = XrayTrafficBytes(uplink = 2_000L, downlink = 1_000L),
            elapsedMillis = 2_000L,
        )

        assertEquals(
            XrayTrafficBytes(uplink = 1_000L, downlink = 500L),
            first.speedBytesPerSecond,
        )
        assertEquals(
            XrayTrafficBytes(uplink = 1_000L, downlink = 500L),
            second.speedBytesPerSecond,
        )
        assertEquals(
            XrayTrafficBytes(uplink = 3_000L, downlink = 1_500L),
            second.totalBytes,
        )
    }

    @Test
    fun excludesFixedLoopbackInboundsFromTrafficTotals() {
        val totals = mapOf(
            "socks-in" to XrayTrafficBytes(uplink = 10L, downlink = 20L),
            XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND to XrayTrafficBytes(uplink = 999L, downlink = 999L),
        )

        assertEquals(
            XrayTrafficBytes(uplink = 10L, downlink = 20L),
            totals.aggregateInboundTraffic(),
        )
    }
}
