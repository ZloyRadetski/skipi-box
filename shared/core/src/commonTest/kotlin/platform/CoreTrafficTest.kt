// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreTrafficTest {
    @Test
    fun parsesCoreSnapshotAndMapsInboundTrafficToTunnelContract() {
        val snapshot = parseCoreTrafficSnapshot(
            """
            {
              "inbound": {
                "socks-in": { "uplink": 10, "downlink": 20 },
                "loopback": { "uplink": 999, "downlink": 999 },
                "": { "uplink": 1, "downlink": 1 }
              },
              "outbound": {
                "proxy-policy-lte": { "uplink": 30, "downlink": 40 },
                "negative": { "uplink": -1, "downlink": -1 }
              }
            }
            """.trimIndent(),
        )

        requireNotNull(snapshot)
        assertEquals(CoreTrafficBytes(uplink = 10, downlink = 20), snapshot.inbound["socks-in"])
        assertEquals(
            CoreTrafficBytes(uplink = 30, downlink = 40),
            snapshot.outbound["proxy-policy-lte"],
        )
        assertEquals(CoreTrafficBytes(), snapshot.outbound["negative"])
        assertEquals(
            TunnelTraffic(uploadBytes = 10, downloadBytes = 20),
            snapshot.inbound.aggregateCoreInboundTraffic(setOf("loopback")).toTunnelTraffic(),
        )
    }

    @Test
    fun rejectsMalformedCoreSnapshot() {
        assertNull(parseCoreTrafficSnapshot("not-json"))
    }

    @Test
    fun uses_fast_polling_only_for_visible_meaningful_traffic() {
        assertEquals(
            CoreTrafficStatsActivePollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = true,
            ),
        )
        assertEquals(
            CoreTrafficStatsIdlePollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = false,
            ),
        )
        assertEquals(
            CoreTrafficStatsScreenOffPollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = false,
                hasMeaningfulTraffic = true,
            ),
        )
    }

    @Test
    fun notification_requests_obey_shared_polling_limits() {
        assertEquals(
            CoreTrafficStatsScreenOffPollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = false,
                hasMeaningfulTraffic = false,
                requestedRefreshIntervalMillis = 1_000L,
            ),
        )
        assertEquals(
            CoreTrafficStatsActivePollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = true,
                requestedRefreshIntervalMillis = 10_000L,
                hasDefaultFrequencyConsumer = true,
            ),
        )
        assertEquals(
            1_000L,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = false,
                requestedRefreshIntervalMillis = 1_000L,
            ),
        )
    }
}
