// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import app.skipi.app.tools.DnsLeakTestUiState
import features.tools.dnsleak.DnsLeakExitInfo
import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakResolver
import features.tools.dnsleak.DnsLeakResolverStatus
import features.tools.dnsleak.DnsLeakSilentServer
import features.tools.dnsleak.DnsLeakStage
import features.tools.dnsleak.DnsLeakTestOutcome
import features.tools.dnsleak.DnsLeakVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkipiDnsLeakModelTest {
    private val exit = DnsLeakExitInfo("203.0.113.9", "DE", "Germany", "Hetzner", 24940)

    private fun resolver(
        server: String,
        country: String,
        system: Boolean = false,
        subnet: String? = null,
        isp: String = "Provider $server",
    ) = DnsLeakResolver(
        server = server,
        isSystemServer = system,
        egressIp = "198.51.100.1",
        clientSubnetIp = subnet,
        observedCountryCode = country,
        observedCountryName = "",
        isp = isp,
        asn = 64500,
    )

    private fun finished(
        resolvers: List<DnsLeakResolver>,
        verdict: DnsLeakVerdict,
        silent: List<DnsLeakSilentServer> = emptyList(),
    ) = DnsLeakTestUiState(outcome = DnsLeakTestOutcome(resolvers, verdict, exit, silent))

    @Test
    fun anIdleScreenHasNoResults() {
        val model = dnsLeakScreenModel(DnsLeakTestUiState())

        assertEquals(DnsLeakHeadline.Idle, model.headline)
        assertTrue(model.systemRows.isEmpty() && model.publicRows.isEmpty())
        assertNull(model.exit)
    }

    @Test
    fun aRunningScreenShowsTheStageAndHidesAnyResult() {
        val state = finished(listOf(resolver("8.8.8.8", "RU")), DnsLeakVerdict.SuspectedLeak)
            .copy(running = true, stage = DnsLeakStage.Locating)

        val model = dnsLeakScreenModel(state)

        assertEquals(DnsLeakHeadline.Running, model.headline)
        assertEquals(DnsLeakStage.Locating, model.stage)
        assertTrue(model.systemRows.isEmpty() && model.publicRows.isEmpty())
        assertNull(model.exit)
    }

    @Test
    fun aFailureNamesItsKindAndNeverShowsResults() {
        val tunnel = dnsLeakScreenModel(
            DnsLeakTestUiState(failed = true, failureKind = DnsLeakFailureKind.TunnelNotPassing),
        )
        val offline = dnsLeakScreenModel(
            DnsLeakTestUiState(failed = true, failureKind = DnsLeakFailureKind.NoInternet),
        )
        val other = dnsLeakScreenModel(DnsLeakTestUiState(failed = true, failedReason = "boom"))

        assertEquals(DnsLeakHeadline.FailedTunnel, tunnel.headline)
        assertEquals(DnsLeakHeadline.FailedNoInternet, offline.headline)
        assertEquals(DnsLeakHeadline.Failed, other.headline)
        assertEquals("boom", other.failureDetail)
        assertTrue(tunnel.systemRows.isEmpty() && tunnel.publicRows.isEmpty())
    }

    @Test
    fun resolversAreGroupedBySystemAndPublicWithLeaksFirst() {
        val model = dnsLeakScreenModel(
            finished(
                listOf(
                    resolver("1.1.1.1", "DE"),
                    resolver("192.168.1.1", "DE", system = true),
                    resolver("9.9.9.9", "RU"),
                    resolver("10.0.0.1", "RU", system = true),
                    resolver("8.8.8.8", ""),
                ),
                DnsLeakVerdict.SuspectedLeak,
            ),
        )

        assertEquals(DnsLeakHeadline.SuspectedLeak, model.headline)
        assertEquals(listOf("10.0.0.1", "192.168.1.1"), model.systemRows.map { it.server })
        assertEquals(listOf("9.9.9.9", "8.8.8.8", "1.1.1.1"), model.publicRows.map { it.server })
        assertEquals(
            listOf(DnsLeakResolverStatus.Leak, DnsLeakResolverStatus.Safe),
            model.systemRows.map { it.status },
        )
        assertEquals(2, model.leakCount)
        assertEquals(exit, model.exit)
    }

    @Test
    fun rowsKeepTheEvidenceTheyWereJudgedOn() {
        val model = dnsLeakScreenModel(
            finished(
                listOf(
                    resolver("8.8.8.8", "DE", subnet = "203.0.113.0"),
                    resolver("1.1.1.1", "DE", isp = ""),
                ),
                DnsLeakVerdict.NoLeak,
            ),
        )

        val withSubnet = model.publicRows.single { it.server == "8.8.8.8" }
        assertEquals("203.0.113.0", withSubnet.observedIp)
        assertTrue(withSubnet.viaSubnet)
        assertEquals(64500, withSubnet.asn)
        val egress = model.publicRows.single { it.server == "1.1.1.1" }
        assertEquals("198.51.100.1", egress.observedIp)
        assertEquals(false, egress.viaSubnet)
        assertEquals("", egress.provider)
    }

    @Test
    fun silentServersAreListedSeparatelyFromAnsweringOnes() {
        val model = dnsLeakScreenModel(
            finished(
                listOf(resolver("8.8.8.8", "DE")),
                DnsLeakVerdict.NoLeak,
                silent = listOf(
                    DnsLeakSilentServer("192.168.1.1", isSystemServer = true),
                    DnsLeakSilentServer("9.9.9.9", isSystemServer = false),
                ),
            ),
        )

        assertEquals(listOf("192.168.1.1", "9.9.9.9"), model.unanswered)
        assertEquals(listOf("8.8.8.8"), model.publicRows.map { it.server })
    }

    @Test
    fun anInconclusiveRunKeepsItsUnknownHeadline() {
        val model = dnsLeakScreenModel(finished(emptyList(), DnsLeakVerdict.Unknown))

        assertEquals(DnsLeakHeadline.Unknown, model.headline)
        assertEquals(0, model.leakCount)
    }
}
