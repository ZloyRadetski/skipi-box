// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import engine.network.isIpAddress
import features.tools.ipinfo.IpInfoData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Pauses before each retry: a freshly started tunnel often passes no traffic for a moment. */
val DefaultTunnelIpRetryDelaysMs: List<Long> = listOf(1_500L, 4_000L, 10_000L)

/**
 * Resolves the public address of the running tunnel for the Home panel, or null when every attempt
 * failed. IPv4 is preferred for display; a lookup that returns no address counts as failed.
 */
suspend fun resolveTunnelIp(
    retryDelaysMs: List<Long> = DefaultTunnelIpRetryDelaysMs,
    fetch: suspend () -> IpInfoData,
): ProxyHomeTunnelIp? {
    for (attempt in 0..retryDelaysMs.size) {
        if (attempt > 0) delay(retryDelaysMs[attempt - 1])
        val info = try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val address = info?.ipv4?.takeIf(String::isNotBlank) ?: info?.ipv6?.takeIf(String::isNotBlank)
        if (info != null && address != null) {
            return ProxyHomeTunnelIp(value = address, flag = info.flagEmoji.takeIf(String::isNotBlank))
        }
    }
    return null
}

/**
 * The address of the server a config leads to, ready for the IP tile: an IP address as it is, a
 * domain resolved through [lookup]. Null when there is no address or it does not resolve.
 */
suspend fun resolveServerAddressIp(host: String?, lookup: suspend (String) -> String?): ProxyHomeTunnelIp? {
    val address = host?.trim()?.removeSurrounding("[", "]")?.takeIf(String::isNotEmpty) ?: return null
    if (isIpAddress(address)) return ProxyHomeTunnelIp(value = address)
    val resolved = try {
        lookup(address)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    return resolved?.trim()?.takeIf(String::isNotEmpty)?.let { ProxyHomeTunnelIp(value = it) }
}

/**
 * Feeds the IP tile: the server's own address first, so the tile is never a dash while the tunnel's
 * public address is being asked for, then the public address once known. A failed public lookup
 * leaves the server address in place.
 */
suspend fun resolveTunnelIpWithServerFallback(
    serverHost: String?,
    lookupHost: suspend (String) -> String?,
    publish: (ProxyHomeTunnelIp?) -> Unit,
    retryDelaysMs: List<Long> = DefaultTunnelIpRetryDelaysMs,
    fetch: suspend () -> IpInfoData,
) {
    resolveServerAddressIp(serverHost, lookupHost)?.let(publish)
    resolveTunnelIp(retryDelaysMs, fetch)?.let(publish)
}
