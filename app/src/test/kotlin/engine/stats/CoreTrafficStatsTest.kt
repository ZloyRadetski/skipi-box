// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import engine.xray.XrayTags
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreTrafficStatsTest {
    @Test
    fun parsesInProcessCoreSnapshotAndPreservesOutboundTags() {
        val snapshot = parseCoreTrafficStatsSnapshot(
            """
            {
              "inbound": {
                "socks-in": { "uplink": 10, "downlink": 20 },
                "${XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND}": { "uplink": 999, "downlink": 999 }
              },
              "outbound": {
                "proxy-policy-lte": { "uplink": 30, "downlink": 40 }
              }
            }
            """.trimIndent(),
        )

        requireNotNull(snapshot)
        assertEquals(XrayTrafficBytes(uplink = 10, downlink = 20), snapshot.inbound["socks-in"])
        assertEquals(
            XrayTrafficBytes(uplink = 30, downlink = 40),
            snapshot.outbound["proxy-policy-lte"],
        )
        assertEquals(
            XrayTrafficBytes(uplink = 10, downlink = 20),
            snapshot.inbound.aggregateInboundTraffic(),
        )
    }

    @Test
    fun rejectsMalformedCoreSnapshot() {
        assertNull(parseCoreTrafficStatsSnapshot("not-json"))
    }
}
