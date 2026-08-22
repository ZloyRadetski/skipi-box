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

/**
 * Why the test could not collect any data at all; picks a localized UI hint.
 */
enum class DnsLeakFailureKind {
    /** No tunnel was up, and nothing answered on the direct network path. */
    NoInternet,

    /** A VPN tunnel was detected, but no traffic passes through it. */
    TunnelNotPassing,
}

/** Verdict of a completed DNS leak test. */
enum class DnsLeakVerdict {
    /** Every tested path exposes the same network as the HTTP exit. */
    NoLeak,

    /** At least one path resolves through a different network than the exit. */
    SuspectedLeak,

    /** Not enough data to judge. */
    Unknown,
}

/** Pure analysis helpers over raw probe results; unit tested. */
object DnsLeakAnalysis {
    private val json = Json { ignoreUnknownKeys = true }

    private val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    fun parseIpWhoIs(body: String): IpWhoIsResponse? {
        return runCatching { json.decodeFromString<IpWhoIsResponse>(body) }
            .getOrNull()
            ?.takeIf { it.success }
    }

    /** Picks the resolver-chain egress IP out of the echo TXT records. */
    fun egressIpFromTxt(records: List<String>): String? {
        return records.firstOrNull { record -> ipv4Regex.matches(record.trim()) }
            ?.trim()
    }

    /** Extracts the base address from an "edns0-client-subnet a.b.c.d/n" record. */
    fun clientSubnetFromTxt(records: List<String>): String? {
        val record = records.firstOrNull { it.startsWith(EdnsClientSubnetPrefix) } ?: return null
        val value = record.substringAfter(EdnsClientSubnetPrefix).trim()
        val address = value.substringBefore("/").trim()
        return address.takeIf { ipv4Regex.matches(it) }
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
        if (resolvers.isEmpty() || exit == null) return DnsLeakVerdict.Unknown
        val comparable = resolvers.filter { it.observedCountryCode.isNotBlank() }
        if (comparable.isEmpty()) return DnsLeakVerdict.Unknown
        return if (comparable.any {
                !it.observedCountryCode.equals(exit.countryCode, ignoreCase = true)
            }
        ) {
            DnsLeakVerdict.SuspectedLeak
        } else {
            DnsLeakVerdict.NoLeak
        }
    }

    /**
     * Maps the presence of a bound VPN network onto the failure kind reported
     * to the UI: with a tunnel detected the probes went through it, so total
     * silence means the tunnel passes no traffic rather than being offline.
     */
    fun failureKind(hasVpnNetwork: Boolean): DnsLeakFailureKind {
        return if (hasVpnNetwork) DnsLeakFailureKind.TunnelNotPassing else DnsLeakFailureKind.NoInternet
    }

    /** Converts a two-letter country code into its flag emoji, e.g. "DE" -> 🇩🇪. */
    fun countryFlagEmoji(countryCode: String?): String? {
        val code = countryCode?.trim()?.uppercase() ?: return null
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
        return buildString {
            appendCodePoint(RegionalIndicatorA + (code[0] - 'A'))
            appendCodePoint(RegionalIndicatorA + (code[1] - 'A'))
        }
    }

    const val EdnsClientSubnetPrefix = "edns0-client-subnet"
    private const val RegionalIndicatorA = 0x1F1E6
}

/** Raw result of probing one DNS server. */
data class DnsProbeResult(
    val server: String,
    val isSystemServer: Boolean,
    val egressIp: String,
    val clientSubnetIp: String?,
)
