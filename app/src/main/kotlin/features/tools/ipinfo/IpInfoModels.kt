// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class IpWhoIsFullResponse(
    val ip: String = "",
    val success: Boolean = false,
    val type: String = "IPv4",
    val continent: String = "",
    @SerialName("continent_code") val continentCode: String = "",
    val country: String = "",
    @SerialName("country_code") val countryCode: String = "",
    val region: String = "",
    @SerialName("region_code") val regionCode: String = "",
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("is_eu") val isEu: Boolean? = null,
    val postal: String = "",
    @SerialName("calling_code") val callingCode: String = "",
    val capital: String = "",
    val borders: String = "",
    val flag: IpWhoIsFlag? = null,
    val connection: IpWhoIsConnectionDetails? = null,
    val timezone: IpWhoIsTimezone? = null,
    val message: String? = null,
)

@Serializable
internal data class IpWhoIsFlag(
    val img: String = "",
    val emoji: String = "",
    @SerialName("emoji_unicode") val emojiUnicode: String = "",
)

@Serializable
internal data class IpWhoIsConnectionDetails(
    val asn: Int? = null,
    val org: String = "",
    val isp: String = "",
    val domain: String = "",
)

@Serializable
internal data class IpWhoIsTimezone(
    val id: String = "",
    val abbr: String = "",
    @SerialName("is_dst") val isDst: Boolean = false,
    val offset: Int = 0,
    val utc: String = "",
    @SerialName("current_time") val currentTime: String = "",
)

/**
 * Clean aggregate presentation model consumed by the UI.
 */
data class IpInfoData(
    val ipv4: String?,
    val ipv6: String?,
    val country: String,
    val countryCode: String,
    val flagEmoji: String,
    val continent: String,
    val region: String,
    val city: String,
    val postal: String,
    val capital: String,
    val latitude: Double?,
    val longitude: Double?,
    val isp: String,
    val org: String,
    val asn: Int?,
    val domain: String,
    val timezoneId: String,
    val timezoneAbbr: String,
    val timezoneUtc: String,
    val currentTime: String,
    val isVpnTunnel: Boolean,
) {
    /** Primary display IP (prefers IPv4 if available, otherwise IPv6). */
    val primaryIp: String
        get() = ipv4 ?: ipv6.orEmpty()

    val ipType: String
        get() = when {
            ipv4 != null && ipv6 != null -> "Dual Stack"
            ipv6 != null -> "IPv6"
            else -> "IPv4"
        }

    /** Formats a full text summary for one-tap copying. */
    fun toSummaryText(): String = buildString {
        if (!ipv4.isNullOrBlank()) {
            appendLine("IPv4: $ipv4")
        }
        if (!ipv6.isNullOrBlank()) {
            appendLine("IPv6: $ipv6")
        }
        if (country.isNotBlank() || countryCode.isNotBlank()) {
            val flagStr = if (flagEmoji.isNotBlank()) "$flagEmoji " else ""
            appendLine("Country: $flagStr$country ($countryCode)")
        }
        if (city.isNotBlank() || region.isNotBlank()) {
            val loc = listOf(city, region, postal).filter(String::isNotBlank).joinToString(", ")
            appendLine("Location: $loc")
        }
        if (latitude != null && longitude != null) {
            appendLine("Coordinates: $latitude, $longitude")
        }
        if (isp.isNotBlank() || org.isNotBlank()) {
            appendLine("ISP: ${isp.ifBlank { org }}")
            if (org.isNotBlank() && org != isp) {
                appendLine("Org: $org")
            }
        }
        if (asn != null) {
            appendLine("ASN: AS$asn")
        }
        if (domain.isNotBlank()) {
            appendLine("Domain: $domain")
        }
        if (timezoneId.isNotBlank() || timezoneUtc.isNotBlank()) {
            appendLine("Timezone: $timezoneId (UTC $timezoneUtc)")
        }
        if (currentTime.isNotBlank()) {
            appendLine("Local Time: $currentTime")
        }
        appendLine("Route: ${if (isVpnTunnel) "VPN Tunnel / Proxy" else "Direct Connection"}")
    }.trimEnd()
}

/** State for the IP Info session. */
sealed interface IpInfoState {
    data object Idle : IpInfoState
    data object Loading : IpInfoState
    data class Success(val data: IpInfoData) : IpInfoState
    data class Failure(val errorMessage: String?, val isTunnelIssue: Boolean) : IpInfoState
}

internal object IpInfoAnalysis {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseIpWhoIs(body: String): IpWhoIsFullResponse? {
        return runCatching { json.decodeFromString<IpWhoIsFullResponse>(body) }
            .getOrNull()
            ?.takeIf { it.success }
    }

    fun countryFlagEmoji(countryCode: String?): String? {
        if (countryCode == null) return null
        val normalized = countryCode.trim().uppercase()
        if (normalized.length != 2 || !normalized.all { it in 'A'..'Z' }) return null
        val first = Character.toChars(0x1F1E6 + (normalized[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (normalized[1] - 'A'))
        return String(first) + String(second)
    }

    fun mapToIpInfoData(
        response: IpWhoIsFullResponse,
        explicitIpv4: String? = null,
        explicitIpv6: String? = null,
        isVpnTunnel: Boolean,
    ): IpInfoData {
        val flag = response.flag?.emoji?.ifBlank { null }
            ?: countryFlagEmoji(response.countryCode).orEmpty()

        val respIsV6 = response.type.equals("IPv6", ignoreCase = true) || response.ip.contains(":")
        val ipv4 = explicitIpv4?.takeIf { !it.contains(":") }
            ?: if (!respIsV6 && response.ip.isNotBlank()) response.ip else null
        val ipv6 = explicitIpv6?.takeIf { it.contains(":") }
            ?: if (respIsV6 && response.ip.isNotBlank()) response.ip else null

        return IpInfoData(
            ipv4 = ipv4,
            ipv6 = ipv6,
            country = response.country,
            countryCode = response.countryCode,
            flagEmoji = flag,
            continent = response.continent,
            region = response.region,
            city = response.city,
            postal = response.postal,
            capital = response.capital,
            latitude = response.latitude,
            longitude = response.longitude,
            isp = response.connection?.isp.orEmpty(),
            org = response.connection?.org.orEmpty(),
            asn = response.connection?.asn,
            domain = response.connection?.domain.orEmpty(),
            timezoneId = response.timezone?.id.orEmpty(),
            timezoneAbbr = response.timezone?.abbr.orEmpty(),
            timezoneUtc = response.timezone?.utc.orEmpty(),
            currentTime = response.timezone?.currentTime.orEmpty(),
            isVpnTunnel = isVpnTunnel,
        )
    }
}
