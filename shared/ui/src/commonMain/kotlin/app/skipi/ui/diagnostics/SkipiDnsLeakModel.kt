// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import app.skipi.app.tools.DnsLeakTestUiState
import features.tools.dnsleak.DnsLeakAnalysis
import features.tools.dnsleak.DnsLeakExitInfo
import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakResolver
import features.tools.dnsleak.DnsLeakResolverStatus
import features.tools.dnsleak.DnsLeakStage
import features.tools.dnsleak.DnsLeakVerdict

/** The one-line state of the whole test, from which the screen picks its colour, wording and icon. */
enum class DnsLeakHeadline { Idle, Running, NoLeak, SuspectedLeak, Unknown, Failed, FailedNoInternet, FailedTunnel }

/** One answering DNS path as the list shows it, with the evidence its status rests on. */
data class DnsLeakRowModel(
    val server: String,
    val countryCode: String,
    /** The provider of the network the path exposed; blank when its geo lookup gave none. */
    val provider: String,
    val observedIp: String?,
    /** True when [observedIp] is the EDNS client subnet (the strongest signal), false for the resolver egress. */
    val viaSubnet: Boolean,
    val asn: Int?,
    val status: DnsLeakResolverStatus,
)

data class DnsLeakScreenModel(
    val headline: DnsLeakHeadline,
    val failureDetail: String?,
    val stage: DnsLeakStage?,
    val exit: DnsLeakExitInfo?,
    val systemRows: List<DnsLeakRowModel>,
    val publicRows: List<DnsLeakRowModel>,
    /** Servers that never answered, so a missing path is visible instead of silently absent. */
    val unanswered: List<String>,
    val leakCount: Int,
)

/** Projects the controller state onto what the screen shows; results of a run in progress or a failed run are never shown. */
fun dnsLeakScreenModel(state: DnsLeakTestUiState): DnsLeakScreenModel {
    val outcome = state.outcome.takeIf { !state.running && !state.failed }
    val headline = when {
        state.running -> DnsLeakHeadline.Running
        state.failed -> when (state.failureKind) {
            DnsLeakFailureKind.NoInternet -> DnsLeakHeadline.FailedNoInternet
            DnsLeakFailureKind.TunnelNotPassing -> DnsLeakHeadline.FailedTunnel
            null -> DnsLeakHeadline.Failed
        }

        outcome == null -> DnsLeakHeadline.Idle
        outcome.verdict == DnsLeakVerdict.NoLeak -> DnsLeakHeadline.NoLeak
        outcome.verdict == DnsLeakVerdict.SuspectedLeak -> DnsLeakHeadline.SuspectedLeak
        else -> DnsLeakHeadline.Unknown
    }
    val rows = outcome?.resolvers.orEmpty().map { resolver -> resolver to resolver.toRow(outcome?.exit) }
    fun group(system: Boolean) = rows.filter { (resolver, _) -> resolver.isSystemServer == system }
        .map { (_, row) -> row }
        .sortedBy { row -> row.status.listPriority() }
    return DnsLeakScreenModel(
        headline = headline,
        failureDetail = state.failedReason.takeIf { state.failed && state.failureKind == null },
        stage = state.stage.takeIf { state.running },
        exit = outcome?.exit,
        systemRows = group(system = true),
        publicRows = group(system = false),
        unanswered = outcome?.silentServers.orEmpty().map { it.server },
        leakCount = rows.count { (_, row) -> row.status == DnsLeakResolverStatus.Leak },
    )
}

/** Leaks come first, then what could not be judged, then the paths that are fine. */
private fun DnsLeakResolverStatus.listPriority(): Int = when (this) {
    DnsLeakResolverStatus.Leak -> 0
    DnsLeakResolverStatus.Unknown -> 1
    DnsLeakResolverStatus.Safe -> 2
}

private fun DnsLeakResolver.toRow(exit: DnsLeakExitInfo?) = DnsLeakRowModel(
    server = server,
    countryCode = observedCountryCode,
    provider = isp,
    observedIp = observedIp,
    viaSubnet = clientSubnetIp != null,
    asn = asn,
    status = DnsLeakAnalysis.status(this, exit),
)
