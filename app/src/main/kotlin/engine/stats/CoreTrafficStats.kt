// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A process-private snapshot supplied by SkipiCore. Unlike Xray's gRPC
 * StatsService, reading it does not require a localhost listener.
 */
internal data class CoreTrafficStatsSnapshot(
    val inbound: Map<String, XrayTrafficBytes> = emptyMap(),
    val outbound: Map<String, XrayTrafficBytes> = emptyMap(),
)

internal fun parseCoreTrafficStatsSnapshot(raw: String): CoreTrafficStatsSnapshot? {
    if (raw.isBlank()) return null
    return runCatching {
        val payload = CoreTrafficStatsJson.decodeFromString<CoreTrafficStatsSnapshotPayload>(raw)
        CoreTrafficStatsSnapshot(
            inbound = payload.inbound.toTrafficBytes(),
            outbound = payload.outbound.toTrafficBytes(),
        )
    }.getOrNull()
}

internal fun Map<String, XrayTrafficBytes>.aggregateInboundTraffic(
    excludedInboundTags: Set<String> = xrayTrafficExcludedInboundTags(),
): XrayTrafficBytes {
    return asSequence()
        .filter { (tag, _) -> tag !in excludedInboundTags }
        .fold(XrayTrafficBytes()) { total, (_, bytes) -> total + bytes }
}

private fun Map<String, CoreTrafficStatsBytesPayload>.toTrafficBytes(): Map<String, XrayTrafficBytes> {
    return entries
        .asSequence()
        .map { (tag, bytes) ->
            tag to XrayTrafficBytes(
                uplink = bytes.uplink.coerceAtLeast(0L),
                downlink = bytes.downlink.coerceAtLeast(0L),
            )
        }
        .filter { (tag, _) -> tag.isNotBlank() }
        .toMap()
}

@Serializable
private data class CoreTrafficStatsSnapshotPayload(
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
