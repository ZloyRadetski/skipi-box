// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

/** Data used to create a concise copyable summary of IP lookup results. */
data class IpInfoSummaryInput(
    val ipv4: String?, val ipv6: String?, val country: String, val countryCode: String,
    val flagEmoji: String, val city: String, val region: String, val postal: String,
    val latitude: Double?, val longitude: Double?, val isp: String, val org: String,
    val asn: Int?, val domain: String, val timezoneId: String, val timezoneUtc: String,
    val currentTime: String, val isVpnTunnel: Boolean,
)

object IpInfoSummary {
    fun format(data: IpInfoSummaryInput): String = buildString {
        if (!data.ipv4.isNullOrBlank()) appendLine("IPv4: ${data.ipv4}")
        if (!data.ipv6.isNullOrBlank()) appendLine("IPv6: ${data.ipv6}")
        if (data.country.isNotBlank() || data.countryCode.isNotBlank()) {
            val flag = if (data.flagEmoji.isNotBlank()) "${data.flagEmoji} " else ""
            appendLine("Country: $flag${data.country} (${data.countryCode})")
        }
        if (data.city.isNotBlank() || data.region.isNotBlank()) {
            appendLine("Location: ${listOf(data.city, data.region, data.postal).filter(String::isNotBlank).joinToString(", ")}")
        }
        if (data.latitude != null && data.longitude != null) appendLine("Coordinates: ${data.latitude}, ${data.longitude}")
        if (data.isp.isNotBlank() || data.org.isNotBlank()) {
            appendLine("ISP: ${data.isp.ifBlank { data.org }}")
            if (data.org.isNotBlank() && data.org != data.isp) appendLine("Org: ${data.org}")
        }
        data.asn?.let { appendLine("ASN: AS$it") }
        if (data.domain.isNotBlank()) appendLine("Domain: ${data.domain}")
        if (data.timezoneId.isNotBlank() || data.timezoneUtc.isNotBlank()) {
            appendLine("Timezone: ${data.timezoneId} (UTC ${data.timezoneUtc})")
        }
        if (data.currentTime.isNotBlank()) appendLine("Local Time: ${data.currentTime}")
        appendLine("Route: ${if (data.isVpnTunnel) "VPN Tunnel / Proxy" else "Direct Connection"}")
    }.trimEnd()
}
