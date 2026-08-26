// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import features.logs.AndroidAppLogger
import engine.network.isIpv4Address
import engine.network.isIpv6Address
import features.proxy.server.model.normalizedServerHost
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val dnsResolutionExecutor = Executors.newFixedThreadPool(8) { runnable ->
    Thread(runnable, "skipi-dns-resolve").apply { isDaemon = true }
}

private const val DnsLookupTimeoutMillis = 600L

internal fun AppState.xrayDnsHosts(proxyServerHosts: List<String>): List<String> {
    if (!enableResolveProxyServerDomain) return dnsHosts
    val candidateHosts = proxyServerHosts
        .map { it.normalizedServerHost() }
        .filter { host ->
            host.isNotBlank() &&
                !isIpv4Address(host) &&
                !isIpv6Address(host) &&
                !host.equals("localhost", ignoreCase = true)
        }
        .distinct()
    if (candidateHosts.isEmpty()) return dnsHosts

    val futures = candidateHosts.map { host ->
        CompletableFuture.supplyAsync({
            val addresses = host.resolveHostAddresses()
            if (addresses.isEmpty()) null else "$host:${addresses.joinToString(",")}"
        }, dnsResolutionExecutor)
    }

    val resolvedEntries = futures.mapNotNull { future ->
        runCatching {
            future.get(DnsLookupTimeoutMillis, TimeUnit.MILLISECONDS)
        }.onFailure {
            future.cancel(true)
        }.getOrNull()
    }

    return (dnsHosts + resolvedEntries).distinct()
}

private fun String.resolveHostAddresses(): List<String> {
    val host = this
    return runCatching {
        InetAddress.getAllByName(host)
            .mapNotNull { address -> address.hostAddress?.substringBefore('%') }
            .filter(String::isNotBlank)
            .distinct()
    }.onFailure { error ->
        AndroidAppLogger.warn(LogTag, "Failed to resolve proxy server host via system DNS: $host", error)
    }.getOrDefault(emptyList())
}

private const val LogTag = "VpnDnsHosts"
