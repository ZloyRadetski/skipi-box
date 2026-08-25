// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

import android.net.Network
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import features.logs.AndroidAppLogger
import features.tools.dnsleak.withProxyAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL

internal class IpInfoEngine(
    private val vpnNetwork: Network? = null,
    private val proxyOptions: LocalProxyOptions? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetch(): IpInfoData = withContext(ioDispatcher) {
        val isVpnTunnel = vpnNetwork != null || proxyOptions != null
        try {
            // Concurrently query:
            // 1. api.ipify.org (strictly IPv4)
            // 2. api64.ipify.org (returns IPv6 if IPv6 egress is active)
            // 3. ipwho.is (direct geo details for current connection)
            val ipv4Deferred = async {
                httpGetText(Ipv4OnlyUrl)?.trim()?.takeIf { it.isNotBlank() && !it.contains(":") }
            }
            val ipv6Deferred = async {
                httpGetText(DualStackIpUrl)?.trim()?.takeIf { it.isNotBlank() && it.contains(":") }
            }
            val directGeoDeferred = async {
                httpGetText(PrimaryGeoUrl)
            }

            val directJson = directGeoDeferred.await()
            val ipv4 = ipv4Deferred.await()
            val ipv6 = ipv6Deferred.await()

            val parsed = directJson?.let(IpInfoAnalysis::parseIpWhoIs)
            if (parsed != null) {
                return@withContext IpInfoAnalysis.mapToIpInfoData(
                    response = parsed,
                    explicitIpv4 = ipv4,
                    explicitIpv6 = ipv6,
                    isVpnTunnel = isVpnTunnel,
                )
            }

            // Fallback: if root ipwho.is failed, try querying by explicit IPv4 or IPv6
            val fallbackTargetIp = ipv4 ?: ipv6
            if (fallbackTargetIp != null) {
                val fallbackGeoJson = httpGetText("$PrimaryGeoUrl/$fallbackTargetIp")
                val fallbackParsed = fallbackGeoJson?.let(IpInfoAnalysis::parseIpWhoIs)
                if (fallbackParsed != null) {
                    return@withContext IpInfoAnalysis.mapToIpInfoData(
                        response = fallbackParsed,
                        explicitIpv4 = ipv4,
                        explicitIpv6 = ipv6,
                        isVpnTunnel = isVpnTunnel,
                    )
                }

                // If geo fails but IP was retrieved, construct a minimal IP model
                return@withContext IpInfoData(
                    ipv4 = ipv4,
                    ipv6 = ipv6,
                    country = "",
                    countryCode = "",
                    flagEmoji = "",
                    continent = "",
                    region = "",
                    city = "",
                    postal = "",
                    capital = "",
                    latitude = null,
                    longitude = null,
                    isp = "",
                    org = "",
                    asn = null,
                    domain = "",
                    timezoneId = "",
                    timezoneAbbr = "",
                    timezoneUtc = "",
                    currentTime = "",
                    isVpnTunnel = isVpnTunnel,
                )
            }

            throw IOException(if (isVpnTunnel) TunnelDownMessage else OfflineMessage)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AndroidAppLogger.warn(LogTag, "Failed to fetch IP details", error)
            throw error
        }
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
                    connection.setRequestProperty("Accept", "application/json, text/plain, */*")
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
        private const val LogTag = "IpInfoEngine"
        private const val PrimaryGeoUrl = "https://ipwho.is"
        private const val Ipv4OnlyUrl = "https://api.ipify.org"
        private const val DualStackIpUrl = "https://api64.ipify.org"
        private const val RequestUserAgent =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private const val TunnelDownMessage =
            "VPN tunnel is up but passes no traffic"
        private const val OfflineMessage =
            "No internet connection"
    }
}
