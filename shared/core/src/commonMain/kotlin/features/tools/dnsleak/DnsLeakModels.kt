// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Geo response of ipwho.is for a single IP address. */
@Serializable
data class IpWhoIsResponse(
    val ip: String = "",
    val success: Boolean = false,
    val country: String = "",
    @SerialName("country_code") val countryCode: String = "",
    val connection: IpWhoIsConnection? = null,
)

@Serializable
data class IpWhoIsConnection(
    val asn: Int? = null,
    val isp: String = "",
    val org: String = "",
)

/**
 * One tested DNS path. [server] is the DNS server we talked to directly;
 * [egressIp] is the resolver chain egress reported by Google's echo record and
 * [clientSubnetIp] is the EDNS Client Subnet base address - the network the
 * resolver believed our query came from. The latter is the strongest leak
 * signal, so it wins when both are available.
 */
data class DnsLeakResolver(
    val server: String,
    val isSystemServer: Boolean,
    val egressIp: String,
    val clientSubnetIp: String?,
    val observedCountryCode: String,
    val observedCountryName: String,
    val isp: String,
    val asn: Int?,
) {
    /** The IP whose geo best represents what this path exposes about us. */
    val observedIp: String?
        get() = clientSubnetIp ?: egressIp.takeIf { it.isNotBlank() }
}

/** The HTTP exit of the device, used as the reference point for the verdict. */
data class DnsLeakExitInfo(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val isp: String,
    val asn: Int?,
)

/** Pure analysis helpers over raw probe results; unit tested. */
object DnsLeakAnalysis {
    private val json = Json { ignoreUnknownKeys = true }

    private val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    fun parseIpWhoIs(body: String): IpWhoIsResponse? {
        return runCatching { json.decodeFromString<IpWhoIsResponse>(body) }
            .getOrNull()
            ?.takeIf { it.success }
    }

    /** Picks the resolver-chain egress IP (IPv4 or IPv6) out of the echo TXT records. */
    fun egressIpFromTxt(records: List<String>): String? {
        return records.firstOrNull { record -> isIpAddress(record.trim()) }
            ?.trim()
    }

    /** Extracts the base address from an "edns0-client-subnet <address>/n" record. */
    fun clientSubnetFromTxt(records: List<String>): String? {
        val record = records.firstOrNull { it.startsWith(EdnsClientSubnetPrefix) } ?: return null
        val value = record.substringAfter(EdnsClientSubnetPrefix).trim()
        val address = value.substringBefore("/").trim()
        return address.takeIf(::isIpAddress)
    }

    private fun isIpAddress(value: String): Boolean = ipv4Regex.matches(value) || isIpv6Address(value)

    private fun isIpv6Address(value: String): Boolean {
        if (value.count { it == ':' } < 2) return false
        if (value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' && it != ':' }) return false
        val compression = value.indexOf("::")
        if (compression != value.lastIndexOf("::")) return false
        fun groups(part: String): List<String>? =
            if (part.isEmpty()) emptyList() else part.split(':').takeIf { list -> list.all { it.length in 1..4 } }
        if (compression < 0) return groups(value)?.size == 8
        val head = groups(value.substring(0, compression)) ?: return false
        val tail = groups(value.substring(compression + 2)) ?: return false
        return head.size + tail.size <= 7
    }

    /** Whether this one path exposes the same country as the HTTP exit; paths that cannot be compared stay unknown. */
    fun status(resolver: DnsLeakResolver, exit: DnsLeakExitInfo?): DnsLeakResolverStatus {
        return when (DnsLeakResultPolicy.verdict(listOf(resolver.observedCountryCode), exit?.countryCode)) {
            DnsLeakVerdict.NoLeak -> DnsLeakResolverStatus.Safe
            DnsLeakVerdict.SuspectedLeak -> DnsLeakResolverStatus.Leak
            DnsLeakVerdict.Unknown -> DnsLeakResolverStatus.Unknown
        }
    }

    fun resolvers(
        probes: List<DnsProbeResult>,
        geoByIp: Map<String, IpWhoIsResponse>,
    ): List<DnsLeakResolver> {
        return probes.mapNotNull { probe ->
            val observedIp = probe.clientSubnetIp ?: probe.egressIp.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val geo = geoByIp[observedIp]
            DnsLeakResolver(
                server = probe.server,
                isSystemServer = probe.isSystemServer,
                egressIp = probe.egressIp,
                clientSubnetIp = probe.clientSubnetIp,
                observedCountryCode = geo?.countryCode.orEmpty(),
                observedCountryName = geo?.country.orEmpty(),
                isp = geo?.connection?.isp ?: geo?.connection?.org.orEmpty(),
                asn = geo?.connection?.asn,
            )
        }
    }

    /**
     * A path leaks when the network it exposes does not match the HTTP exit
     * network. With the VPN down the exit simply equals the home network, so
     * every path matches and the test stays informative rather than alarming.
     */
    fun verdict(
        resolvers: List<DnsLeakResolver>,
        exit: DnsLeakExitInfo?,
    ): DnsLeakVerdict {
        val statuses = resolvers.map { status(it, exit) }
        return when {
            DnsLeakResolverStatus.Leak in statuses -> DnsLeakVerdict.SuspectedLeak
            DnsLeakResolverStatus.Safe in statuses -> DnsLeakVerdict.NoLeak
            else -> DnsLeakVerdict.Unknown
        }
    }

    /**
     * Maps the presence of a bound VPN network onto the failure kind reported
     * to the UI: with a tunnel detected the probes went through it, so total
     * silence means the tunnel passes no traffic rather than being offline.
     */
    fun failureKind(hasVpnNetwork: Boolean): DnsLeakFailureKind {
        return DnsLeakResultPolicy.failureKind(hasVpnNetwork)
    }

    /** Converts a two-letter country code into its flag emoji, e.g. "DE" -> flag. */
    fun countryFlagEmoji(countryCode: String?): String? = features.tools.countryFlagEmoji(countryCode)

    const val EdnsClientSubnetPrefix = "edns0-client-subnet"
}

/** Raw result of probing one DNS server. */
data class DnsProbeResult(
    val server: String,
    val isSystemServer: Boolean,
    val egressIp: String,
    val clientSubnetIp: String?,
)

/** A tested DNS server that never produced a usable answer. */
data class DnsLeakSilentServer(
    val server: String,
    val isSystemServer: Boolean,
)

/** Result of one completed DNS leak test run. */
data class DnsLeakTestOutcome(
    val resolvers: List<DnsLeakResolver>,
    val verdict: DnsLeakVerdict,
    val exit: DnsLeakExitInfo? = null,
    val silentServers: List<DnsLeakSilentServer> = emptyList(),
)

/**
 * Thrown when neither the exit check nor any DNS probe produced an answer.
 * The message names the actionable cause: with a VPN network detected the
 * probes went through it, so total silence points at a dead tunnel rather
 * than a missing internet connection.
 */
class DnsLeakTestFailure(
    hasVpnNetwork: Boolean,
) : Exception(if (hasVpnNetwork) TunnelDownMessage else OfflineMessage) {
    /** Selects the localized hint shown on the test page. */
    val kind: DnsLeakFailureKind = DnsLeakAnalysis.failureKind(hasVpnNetwork)

    private companion object {
        const val TunnelDownMessage =
            "VPN tunnel is up but passes no traffic - reconnect the VPN or change the server"
        const val OfflineMessage = "No internet connection - check the network"
    }
}
