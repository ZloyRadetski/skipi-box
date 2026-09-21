// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-tag byte counters reported by the in-process SKIPI Core.
 *
 * `uplink` and `downlink` retain the names used by Xray's statistics API;
 * [toTunnelTraffic] maps them to the platform-neutral tunnel contract.
 */
data class CoreTrafficBytes(
    val uplink: Long = 0L,
    val downlink: Long = 0L,
) {
    operator fun plus(other: CoreTrafficBytes): CoreTrafficBytes = CoreTrafficBytes(
        uplink = uplink + other.uplink,
        downlink = downlink + other.downlink,
    )
}

/** A process-private traffic snapshot that needs no Xray API listener. */
data class CoreTrafficSnapshot(
    val inbound: Map<String, CoreTrafficBytes> = emptyMap(),
    val outbound: Map<String, CoreTrafficBytes> = emptyMap(),
)

/**
 * Polls frequently only while an interactive client has meaningful traffic.
 *
 * A platform can pass a requested refresh interval for a notification or
 * widget consumer. Screen-off polling is deliberately never made more
 * frequent by that request.
 */
fun coreTrafficStatsPollIntervalMillis(
    isScreenInteractive: Boolean,
    hasMeaningfulTraffic: Boolean,
    requestedRefreshIntervalMillis: Long? = null,
    hasDefaultFrequencyConsumer: Boolean = false,
): Long {
    val defaultInterval = when {
        !isScreenInteractive -> CoreTrafficStatsScreenOffPollIntervalMillis
        hasMeaningfulTraffic -> CoreTrafficStatsActivePollIntervalMillis
        else -> CoreTrafficStatsIdlePollIntervalMillis
    }
    val requestedInterval = requestedRefreshIntervalMillis ?: return defaultInterval
    return when {
        !isScreenInteractive -> maxOf(defaultInterval, requestedInterval)
        hasDefaultFrequencyConsumer -> minOf(defaultInterval, requestedInterval)
        else -> requestedInterval
    }
}

const val CoreTrafficStatsActivePollIntervalMillis = 2_000L
const val CoreTrafficStatsIdlePollIntervalMillis = 5_000L
const val CoreTrafficStatsScreenOffPollIntervalMillis = 30_000L

/** Parses the stable JSON returned by SKIPI Core's `QueryTrafficStats` API. */
fun parseCoreTrafficSnapshot(raw: String): CoreTrafficSnapshot? {
    if (raw.isBlank()) return null
    return runCatching {
        val payload = CoreTrafficStatsJson.decodeFromString<CoreTrafficSnapshotPayload>(raw)
        CoreTrafficSnapshot(
            inbound = payload.inbound.toCoreTrafficBytes(),
            outbound = payload.outbound.toCoreTrafficBytes(),
        )
    }.getOrNull()
}

/** Aggregates inbound counters while allowing a platform to exclude loopback tags. */
fun Map<String, CoreTrafficBytes>.aggregateCoreInboundTraffic(
    excludedInboundTags: Set<String> = emptySet(),
): CoreTrafficBytes = asSequence()
    .filter { (tag, _) -> tag !in excludedInboundTags }
    .fold(CoreTrafficBytes()) { total, (_, bytes) -> total + bytes }

/** Converts Core terminology to the shared tunnel lifecycle contract. */
fun CoreTrafficBytes.toTunnelTraffic(): TunnelTraffic = TunnelTraffic(
    uploadBytes = uplink,
    downloadBytes = downlink,
)

private fun Map<String, CoreTrafficStatsBytesPayload>.toCoreTrafficBytes(): Map<String, CoreTrafficBytes> = entries
    .asSequence()
    .map { (tag, bytes) ->
        tag to CoreTrafficBytes(
            uplink = bytes.uplink.coerceAtLeast(0L),
            downlink = bytes.downlink.coerceAtLeast(0L),
        )
    }
    .filter { (tag, _) -> tag.isNotBlank() }
    .toMap()

@Serializable
private data class CoreTrafficSnapshotPayload(
    val inbound: Map<String, CoreTrafficStatsBytesPayload> = emptyMap(),
    val outbound: Map<String, CoreTrafficStatsBytesPayload> = emptyMap(),
)

@Serializable
private data class CoreTrafficStatsBytesPayload(
    val uplink: Long = 0L,
    val downlink: Long = 0L,
)

private val CoreTrafficStatsJson = Json {
    ignoreUnknownKeys = true
}
