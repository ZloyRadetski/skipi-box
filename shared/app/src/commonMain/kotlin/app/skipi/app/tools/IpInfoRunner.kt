// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.ipinfo.IpInfoAnalysis
import features.tools.ipinfo.IpInfoData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The lookup could not obtain any address; [isTunnelIssue] selects the tunnel-specific hint. */
class IpInfoFailure(message: String, val isTunnelIssue: Boolean) : Exception(message)

/** Resolves the public IPv4/IPv6 addresses and geo details of the active connection. */
class IpInfoRunner(
    private val http: DiagnosticsHttpClient,
    private val isVpnTunnel: Boolean,
) {
    suspend fun fetch(): IpInfoData = coroutineScope {
        // Query concurrently: strict IPv4, the dual-stack endpoint (IPv6 if egress is active) and
        // the geo details of the current connection.
        val ipv4 = async { text(Ipv4OnlyUrl)?.trim()?.takeIf { it.isNotBlank() && !it.contains(":") } }
        val ipv6 = async { text(DualStackIpUrl)?.trim()?.takeIf { it.isNotBlank() && it.contains(":") } }
        val directGeo = async { text(PrimaryGeoUrl) }

        val directJson = directGeo.await()
        val explicitIpv4 = ipv4.await()
        val explicitIpv6 = ipv6.await()

        directJson?.let(IpInfoAnalysis::parseIpWhoIs)?.let { parsed ->
            return@coroutineScope IpInfoAnalysis.mapToIpInfoData(parsed, explicitIpv4, explicitIpv6, isVpnTunnel)
        }

        // Root lookup failed: try by explicit address, then fall back to a minimal model.
        val fallbackTargetIp = explicitIpv4 ?: explicitIpv6
            ?: throw IpInfoFailure(if (isVpnTunnel) TunnelDownMessage else OfflineMessage, isTunnelIssue = isVpnTunnel)
        text("$PrimaryGeoUrl/$fallbackTargetIp")?.let(IpInfoAnalysis::parseIpWhoIs)?.let { parsed ->
            return@coroutineScope IpInfoAnalysis.mapToIpInfoData(parsed, explicitIpv4, explicitIpv6, isVpnTunnel)
        }
        IpInfoData(
            ipv4 = explicitIpv4, ipv6 = explicitIpv6, country = "", countryCode = "", flagEmoji = "",
            continent = "", region = "", city = "", postal = "", capital = "", latitude = null,
            longitude = null, isp = "", org = "", asn = null, domain = "", timezoneId = "",
            timezoneAbbr = "", timezoneUtc = "", currentTime = "", isVpnTunnel = isVpnTunnel,
        )
    }

    private suspend fun text(url: String) = http.getText(url, accept = "application/json, text/plain, */*")

    private companion object {
        const val PrimaryGeoUrl = "https://ipwho.is"
        const val Ipv4OnlyUrl = "https://api.ipify.org"
        const val DualStackIpUrl = "https://api64.ipify.org"
        const val TunnelDownMessage = "VPN tunnel is up but passes no traffic"
        const val OfflineMessage = "No internet connection"
    }
}
