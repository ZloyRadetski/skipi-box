// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import features.logs.AppLog
import engine.network.isIpv4Address
import engine.network.isIpv6Address
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.normalizedServerHost
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// InetAddress.getAllByName ignores interruption, so a hung lookup keeps its thread until the system resolver
// gives up. Threads are therefore created on demand (never queued behind a hung one) and capped by
// dnsLookupSlots; a lookup that finds every slot taken is skipped instead of waiting.
private val dnsResolutionExecutor = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "skipi-dns-resolve").apply { isDaemon = true }
}
private val dnsLookupSlots = Semaphore(MaxConcurrentDnsLookups)

private fun launchLookup(host: String, resolve: (String) -> List<String>): CompletableFuture<List<String>>? {
    if (!dnsLookupSlots.tryAcquire()) {
        AppLog.warn(LogTag, "Skipped DNS lookup, too many lookups are still hung: $host")
        return null
    }
    return try {
        CompletableFuture.supplyAsync({
            try {
                resolve(host)
            } finally {
                dnsLookupSlots.release()
            }
        }, dnsResolutionExecutor)
    } catch (error: RejectedExecutionException) {
        dnsLookupSlots.release()
        null
    }
}

internal const val MaxConcurrentDnsLookups = 32

private const val DnsLookupTimeoutMillis = 600L

fun AppState.xrayDnsHosts(proxyServerHosts: List<String>): List<String> {
    return xrayDnsHosts(proxyServerHosts, String::resolveHostAddresses)
}

/**
 * Adds temporary bootstrap records for proxy endpoints. Explicit user hosts
 * remain authoritative: resolving the same name again could overwrite a pin
 * with a stale or censored system-DNS answer.
 */
fun AppState.xrayDnsHosts(
    proxyServerHosts: List<String>,
    resolveAddresses: (String) -> List<String>,
): List<String> {
    if (!enableResolveProxyServerDomain) return dnsHosts
    val explicitlyMappedHosts = dnsHosts.asSequence()
        .map { entry -> entry.substringBefore(':').normalizedDnsHostKey() }
        .filter(String::isNotEmpty)
        .toSet()
    val candidateHosts = proxyServerHosts
        .map(String::normalizedDnsHostKey)
        .filter { host ->
            host.isNotBlank() &&
                !isIpv4Address(host) &&
                !isIpv6Address(host) &&
                !host.equals("localhost", ignoreCase = true) &&
                host !in explicitlyMappedHosts
        }
        .distinct()
    if (candidateHosts.isEmpty()) return dnsHosts

    val futures = candidateHosts.mapNotNull { host ->
        launchLookup(host, resolveAddresses)?.let { future -> host to future }
    }

    val resolvedEntries = futures.mapNotNull { (host, future) ->
        runCatching {
            future.get(DnsLookupTimeoutMillis, TimeUnit.MILLISECONDS)
        }.onFailure {
            future.cancel(true)
        }.getOrNull()?.takeIf { addresses -> addresses.isNotEmpty() }?.let { addresses ->
            "$host:${addresses.joinToString(",")}"
        }
    }

    return (dnsHosts + resolvedEntries).distinct()
}

private fun String.normalizedDnsHostKey(): String {
    return normalizedServerHost().removeSuffix(".").lowercase()
}

private fun String.resolveHostAddresses(logFailure: Boolean = true): List<String> {
    val host = this
    return runCatching {
        InetAddress.getAllByName(host)
            .mapNotNull { address -> address.hostAddress?.substringBefore('%') }
            .filter(String::isNotBlank)
            .distinct()
    }.onFailure { error ->
        if (logFailure) {
            AppLog.warn(LogTag, "Failed to resolve proxy server host via system DNS: $host", error)
        }
    }.getOrDefault(emptyList())
}

/**
 * Native AmneziaWG passes its peer endpoint to an IPC parser that accepts an
 * address literal only. Bootstrap a hostname while the application is still
 * outside its own VPN when possible. A failed bootstrap is not fatal: the
 * native core retries the original hostname with its configured DNS before it
 * reaches the IPC parser.
 */
fun AmneziaWg.withNativeRunnerEndpointResolved(
    resolveAddresses: (String) -> List<String> = String::resolveHostAddressesWithinTimeout,
): AmneziaWg {
    val host = server.normalizedServerHost().removeSuffix(".")
    if (host.isBlank() || isIpv4Address(host) || isIpv6Address(host)) {
        return copy(server = host.ifBlank { server })
    }
    val addresses = resolveAddresses(host)
        .asSequence()
        .map { candidate -> candidate.substringBefore('%').trim() }
        .filter { candidate -> isIpv4Address(candidate) || isIpv6Address(candidate) }
        .toList()
    val address = addresses.firstOrNull(::isIpv4Address)
        ?: addresses.firstOrNull(::isIpv6Address)
    if (address == null) {
        AppLog.info(
            LogTag,
            "Deferring AmneziaWG endpoint hostname resolution to native core: $host",
        )
        return copy(server = host)
    }
    return copy(server = address)
}

private fun String.resolveHostAddressesWithinTimeout(): List<String> {
    val host = this
    val future = launchLookup(host) { name -> name.resolveHostAddresses(logFailure = false) } ?: return emptyList()
    return runCatching {
        future.get(DnsLookupTimeoutMillis, TimeUnit.MILLISECONDS)
    }.onFailure {
        future.cancel(true)
        AppLog.info(LogTag, "Native AmneziaWG endpoint bootstrap lookup did not complete: $host")
    }.getOrDefault(emptyList())
}

private const val LogTag = "VpnDnsHosts"
