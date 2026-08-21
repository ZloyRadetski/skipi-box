// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import android.net.Network
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL

/**
 * Self-contained DNS leak detection without any third-party leak service.
 *
 * Every tested DNS path (the system servers plus well-known public resolvers)
 * is asked directly, over raw UDP/TCP, for Google's echo TXT record
 * `o-o.myaddr.l.google.com`. The answer exposes which resolver egress and
 * which EDNS Client Subnet our queries reveal. Those networks are compared
 * with the device's HTTP exit network (api.ipify.org + ipwho.is): when the VPN
 * tunnels DNS properly, all paths expose the exit network; a path that
 * resolves through the home ISP instead is a leak.
 *
 * The app itself is excluded from direct VPN network binding (to protect Xray's
 * outbound connections from looping). When [proxyOptions] is provided, probes
 * and the exit check traverse the tunnel via the local SOCKS proxy, exactly
 * reflecting how the tunnel handles traffic.
 */
internal class DnsLeakTestEngine(
    private val systemDnsServers: List<String>,
    private val onProgress: (Int) -> Unit,
    private val vpnNetwork: Network? = null,
    private val proxyOptions: LocalProxyOptions? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun run(): DnsLeakTestOutcome = withContext(ioDispatcher) {
        try {
            onProgress(0)
            val exit = fetchExitInfo()
            onProgress(1)
            val probes = probeServers()
            onProgress(2)
            val geoByIp = fetchGeoData(
                probes.mapNotNull { probe -> probe.clientSubnetIp ?: probe.egressIp.takeIf(String::isNotBlank) }
                    .distinct(),
            )
            onProgress(3)
            val resolvers = DnsLeakAnalysis.resolvers(probes, geoByIp)
            if (exit == null && resolvers.isEmpty()) {
                throw DnsLeakTestFailure(hasVpnNetwork = vpnNetwork != null || proxyOptions != null)
            }
            DnsLeakTestOutcome(
                resolvers = resolvers,
                verdict = DnsLeakAnalysis.verdict(resolvers, exit),
                exit = exit,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AndroidAppLogger.warn(LogTag, "DNS leak test failed", error)
            throw error
        }
    }

    /** Probes every system and public resolver in parallel, retrying silent servers once. */
    private suspend fun probeServers(): List<DnsProbeResult> = withContext(ioDispatcher) {
        val servers = buildList {
            systemDnsServers.filter { it.isNotBlank() }.forEach { add(it to true) }
            PublicResolvers.forEach { add(it to false) }
        }.distinctBy { it.first }
        servers.map { (server, isSystemServer) ->
            async {
                val records = retrying(ProbeAttempts) {
                    RawDnsClient.queryTxt(
                        server = server,
                        domain = EchoDomain,
                        network = vpnNetwork,
                        proxyOptions = proxyOptions,
                    )
                }
                DnsProbeResult(
                    server = server,
                    isSystemServer = isSystemServer,
                    egressIp = records?.let(DnsLeakAnalysis::egressIpFromTxt).orEmpty(),
                    clientSubnetIp = records?.let(DnsLeakAnalysis::clientSubnetFromTxt),
                )
            }
        }.awaitAll()
    }

    private suspend fun fetchExitInfo(): DnsLeakExitInfo? = withContext(ioDispatcher) {
        val ip = retrying(HttpAttempts) { httpGetText(ExitIpUrl)?.trim()?.takeIf(String::isNotBlank) }
        if (ip == null) {
            AndroidAppLogger.warn(LogTag, "Failed to detect the HTTP exit IP")
            return@withContext null
        }
        val geo = retrying(HttpAttempts) {
            httpGetText("$GeoIpUrl$ip")?.let(DnsLeakAnalysis::parseIpWhoIs)
        }
        DnsLeakExitInfo(
            ip = ip,
            countryCode = geo?.countryCode.orEmpty(),
            countryName = geo?.country.orEmpty(),
            isp = geo?.connection?.isp ?: geo?.connection?.org.orEmpty(),
            asn = geo?.connection?.asn,
        )
    }

    /** Runs [block] up to [attempts] times until it yields a non-null value. */
    private fun <T> retrying(attempts: Int, block: () -> T?): T? {
        var value = block()
        var attempt = 1
        while (value == null && attempt < attempts) {
            Thread.sleep(RetryDelayMillis)
            value = block()
            attempt += 1
        }
        return value
    }

    private suspend fun fetchGeoData(ips: List<String>): Map<String, IpWhoIsResponse> =
        withContext(ioDispatcher) {
            ips.map { ip ->
                async { ip to httpGetText("$GeoIpUrl$ip")?.let(DnsLeakAnalysis::parseIpWhoIs) }
            }.awaitAll().mapNotNull { (ip, geo) -> geo?.let { ip to it } }.toMap()
        }

    private fun httpGetText(url: String): String? {
        return runCatching {
            proxyOptions.withProxyAuthenticator {
                val connection = if (proxyOptions != null) {
                    val host = if (proxyOptions.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
                        LocalProxyLoopbackAddress
                    } else {
                        proxyOptions.listenAddress
                    }
                    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, proxyOptions.port))
                    URI(url).toURL().openConnection(proxy) as HttpURLConnection
                } else {
                    val net = vpnNetwork
                    val conn = if (net != null) {
                        runCatching { net.openConnection(URL(url)) as? HttpURLConnection }.getOrNull()
                    } else {
                        null
                    }
                    conn ?: (URL(url).openConnection() as HttpURLConnection)
                }
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", RequestUserAgent)
                    connection.setRequestProperty("Accept", "*/*")
                    connection.connect()
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withProxyAuthenticator null
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }
        }.getOrElse { error ->
            AndroidAppLogger.debug(LogTag, "HTTP request failed: $url (${error.message})")
            null
        }
    }

    companion object {
        private const val LogTag = "DnsLeakTest"
        private const val EchoDomain = "o-o.myaddr.l.google.com"
        private const val ExitIpUrl = "https://api.ipify.org"
        private const val GeoIpUrl = "https://ipwho.is/"
        private const val RequestUserAgent =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private val PublicResolvers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

        /** Extra attempts for a silent HTTP endpoint or DNS server. */
        private const val ProbeAttempts = 2
        private const val HttpAttempts = 2
        private const val RetryDelayMillis = 400L
    }
}

/**
 * Thrown when neither the exit check nor any DNS probe produced an answer.
 * The message names the actionable cause: with a VPN network detected the
 * probes went through it, so total silence points at a dead tunnel rather
 * than a missing internet connection.
 */
internal class DnsLeakTestFailure(
    hasVpnNetwork: Boolean,
) : IOException(
    if (hasVpnNetwork) TunnelDownMessage else OfflineMessage,
) {
    /** Selects the localized hint shown on the test page. */
    val kind: DnsLeakFailureKind = DnsLeakAnalysis.failureKind(hasVpnNetwork)

    private companion object {
        const val TunnelDownMessage =
            "VPN tunnel is up but passes no traffic - reconnect the VPN or change the server"
        const val OfflineMessage = "No internet connection - check the network"
    }
}

/** Result of one completed DNS leak test run. */
internal data class DnsLeakTestOutcome(
    val resolvers: List<DnsLeakResolver>,
    val verdict: DnsLeakVerdict,
    val exit: DnsLeakExitInfo? = null,
)
