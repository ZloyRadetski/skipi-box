// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

/** Where link merging stands, as the page and the tray show it. */
enum class MultilinkPhase {
    /** The Core library has no link merging. */
    Unsupported,
    Off,

    /** On, and waiting for the tunnel (or for Core to report its links). */
    Waiting,

    /** On, but only one link can take connections now. */
    OneLink,
    Merging,
}

/** One link's part of the traffic, 0..1. */
data class MultilinkShare(val id: String, val fraction: Double)

data class MultilinkOverview(
    val phase: MultilinkPhase,
    val usedLinks: Int = 0,
    val downMbps: Double = 0.0,
    val upMbps: Double = 0.0,
    val flows: Long = 0,
    /** The used links that are online, in Core's order; empty unless merging. */
    val shares: List<MultilinkShare> = emptyList(),
)

/** Sums Core's view of the links the user lets merging use. */
fun multilinkOverview(
    settings: MultilinkSettings,
    status: MultilinkStatus,
    tunnelRunning: Boolean,
    coreSupported: Boolean = true,
): MultilinkOverview {
    val phase = when {
        !coreSupported -> MultilinkPhase.Unsupported
        !settings.enabled -> MultilinkPhase.Off
        !tunnelRunning || !status.enabled -> MultilinkPhase.Waiting
        !status.merging -> MultilinkPhase.OneLink
        else -> MultilinkPhase.Merging
    }
    if (phase != MultilinkPhase.Merging) return MultilinkOverview(phase)
    val used = status.links.filter { it.online && !it.excluded && it.id !in settings.excluded }
    val traffic = used.map { it.mbpsDown() + it.mbpsUp() }
    val total = traffic.sum()
    val shares = used.mapIndexed { index, link ->
        MultilinkShare(link.id, if (total > 0) traffic[index] / total else 1.0 / used.size)
    }
    return MultilinkOverview(
        phase = phase,
        usedLinks = used.size,
        downMbps = used.sumOf { it.mbpsDown() },
        upMbps = used.sumOf { it.mbpsUp() },
        flows = used.sumOf { it.flows },
        shares = shares,
    )
}
