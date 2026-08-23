// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import engine.xray.XrayTags
import java.util.Locale

internal enum class XrayTrafficDirection {
    Uplink,
    Downlink,
}

internal data class XrayTrafficStat(
    val tag: String,
    val direction: XrayTrafficDirection,
    val bytes: Long,
)

internal data class XrayTrafficBytes(
    val uplink: Long = 0L,
    val downlink: Long = 0L,
) {
    operator fun plus(other: XrayTrafficBytes): XrayTrafficBytes {
        return XrayTrafficBytes(
            uplink = uplink + other.uplink,
            downlink = downlink + other.downlink,
        )
    }
}

internal data class XrayTrafficSessionSample(
    val speedBytesPerSecond: XrayTrafficBytes,
    val totalBytes: XrayTrafficBytes,
)

internal const val MinimumMeaningfulTrafficDeltaBytes = 2_048L

/** Returns the outbound that carried the most new traffic since the prior sample. */
internal fun Map<String, XrayTrafficBytes>.maxTrafficDeltaComparedTo(
    previous: Map<String, XrayTrafficBytes>,
    currentActiveTag: String? = null,
    minDeltaBytes: Long = MinimumMeaningfulTrafficDeltaBytes,
): String? {
    val deltas = entries
        .map { (tag, current) ->
            val before = previous[tag] ?: XrayTrafficBytes()
            tag to ((current.uplink - before.uplink).coerceAtLeast(0L) +
                (current.downlink - before.downlink).coerceAtLeast(0L))
        }
        .filter { (_, delta) -> delta > 0L }

    if (deltas.isEmpty()) return currentActiveTag

    val maxEntry = deltas.maxByOrNull { (_, delta) -> delta } ?: return currentActiveTag

    if (currentActiveTag != null) {
        val currentDelta = deltas.firstOrNull { it.first == currentActiveTag }?.second ?: 0L
        if (currentDelta > 0L && maxEntry.second < currentDelta * 2) {
            return currentActiveTag
        }
    }

    return if (maxEntry.second >= minDeltaBytes) {
        maxEntry.first
    } else {
        currentActiveTag ?: maxEntry.first
    }
}

private data class XrayTrafficSpeedSample(
    val bytes: XrayTrafficBytes,
    val elapsedMillis: Long,
)

internal class XrayTrafficSessionAccumulator {
    private var totalBytes = XrayTrafficBytes()
    private val speedSamples = ArrayDeque<XrayTrafficSpeedSample>()
    private var speedWindowElapsedMillis = 0L

    fun record(
        delta: XrayTrafficBytes,
        elapsedMillis: Long,
    ): XrayTrafficSessionSample {
        val sampleElapsedMillis = elapsedMillis.coerceAtLeast(1L)
        totalBytes += delta
        speedSamples.addLast(
            XrayTrafficSpeedSample(
                bytes = delta,
                elapsedMillis = sampleElapsedMillis,
            ),
        )
        speedWindowElapsedMillis += sampleElapsedMillis
        while (speedSamples.size > 1) {
            val oldest = speedSamples.first()
            if (speedWindowElapsedMillis - oldest.elapsedMillis < TrafficSpeedWindowMillis) break
            speedSamples.removeFirst()
            speedWindowElapsedMillis -= oldest.elapsedMillis
        }

        val speedWindowBytes = speedSamples.fold(XrayTrafficBytes()) { result, sample ->
            result + sample.bytes
        }
        val speedWindowSeconds = speedWindowElapsedMillis.toDouble() / 1000.0
        return XrayTrafficSessionSample(
            speedBytesPerSecond = XrayTrafficBytes(
                uplink = (speedWindowBytes.uplink / speedWindowSeconds).toLong(),
                downlink = (speedWindowBytes.downlink / speedWindowSeconds).toLong(),
            ),
            totalBytes = totalBytes,
        )
    }
}

internal fun parseXrayInboundTrafficStat(
    name: String,
    bytes: Long,
): XrayTrafficStat? {
    return parseXrayTrafficStat(name, bytes, XrayInboundStatPrefix)
}

internal fun parseXrayOutboundTrafficStat(
    name: String,
    bytes: Long,
): XrayTrafficStat? {
    return parseXrayTrafficStat(name, bytes, XrayOutboundStatPrefix)
}

private fun parseXrayTrafficStat(
    name: String,
    bytes: Long,
    prefix: String,
): XrayTrafficStat? {
    val parts = name.split(XrayStatNameSeparator)
    if (parts.size != XrayInboundTrafficStatPartCount) return null
    if (parts[0] != prefix || parts[2] != XrayTrafficStatMiddle) return null
    val direction = when (parts[3]) {
        XrayTrafficStatUplink -> XrayTrafficDirection.Uplink
        XrayTrafficStatDownlink -> XrayTrafficDirection.Downlink
        else -> return null
    }
    return XrayTrafficStat(
        tag = parts[1],
        direction = direction,
        bytes = bytes,
    )
}

internal fun aggregateOutboundTraffic(stats: List<XrayTrafficStat>): Map<String, XrayTrafficBytes> {
    return stats.groupBy(XrayTrafficStat::tag).mapValues { (_, tagStats) ->
        tagStats.fold(XrayTrafficBytes()) { total, stat ->
            when (stat.direction) {
                XrayTrafficDirection.Uplink -> total.copy(uplink = total.uplink + stat.bytes)
                XrayTrafficDirection.Downlink -> total.copy(downlink = total.downlink + stat.bytes)
            }
        }
    }
}

internal fun aggregateInboundTraffic(
    stats: List<XrayTrafficStat>,
    excludedInboundTags: Set<String>,
): XrayTrafficBytes {
    var uplink = 0L
    var downlink = 0L
    stats
        .asSequence()
        .filter { stat -> stat.tag !in excludedInboundTags }
        .forEach { stat ->
            when (stat.direction) {
                XrayTrafficDirection.Uplink -> uplink += stat.bytes
                XrayTrafficDirection.Downlink -> downlink += stat.bytes
            }
        }
    return XrayTrafficBytes(uplink = uplink, downlink = downlink)
}

internal fun Long.toTrafficSizeString(): String {
    var size = toDouble()
    var unitIndex = 0
    while (size >= TrafficUnitThreshold && unitIndex < TrafficUnits.lastIndex) {
        size /= TrafficUnitDivisor
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, TrafficUnits[unitIndex])
}

internal fun Long.toTrafficSpeedString(): String {
    return "${toTrafficSizeString()}/s"
}

private const val XrayStatNameSeparator = ">>>"
private const val XrayInboundTrafficStatPartCount = 4
private const val XrayInboundStatPrefix = "inbound"
private const val XrayOutboundStatPrefix = "outbound"
private const val XrayTrafficStatMiddle = "traffic"
private const val XrayTrafficStatUplink = "uplink"
private const val XrayTrafficStatDownlink = "downlink"
private const val TrafficSpeedWindowMillis = 3_000L
private const val TrafficUnitThreshold = 1000L
private const val TrafficUnitDivisor = 1024.0

private val TrafficUnits = listOf("B", "KB", "MB", "GB", "TB", "PB")
internal fun xrayTrafficExcludedInboundTags(apiTag: String): Set<String> = setOf(
    apiTag,
    XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND,
)
