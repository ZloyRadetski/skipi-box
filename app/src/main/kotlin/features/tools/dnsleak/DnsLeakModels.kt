// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One record of the dnsleak test result feed. Records of type "dns" are the
 * resolvers that actually handled our probe queries; "ip" records describe the
 * client's visible exit; "conclusion" records carry a human-readable summary.
 */
@Serializable
internal data class DnsLeakRecord(
    val ip: String = "",
    @SerialName("country_id") val countryId: String = "",
    @SerialName("country_name") val countryName: String = "",
    val asn: Int? = null,
    val isp: String = "",
    val type: String = "",
)

/** A single detected DNS resolver, deduplicated by IP. */
internal data class DnsLeakResolver(
    val ip: String,
    val countryId: String,
    val countryName: String,
    val asn: Int?,
    val isp: String,
) {
    /** Two-letter country code as an uppercase key, or null when unknown. */
    val countryCode: String?
        get() = countryId.trim().uppercase().takeIf { it.length == 2 }
}

/** Verdict of a completed DNS leak test. */
enum class DnsLeakVerdict {
    /** All probes were resolved by one or two controlled resolver networks. */
    NoLeak,

    /** Probes leaked to three or more distinct resolver networks. */
    SuspectedLeak,

    /** Not enough data to judge. */
    Unknown,
}

/** Pure analysis helpers over the raw result feed; unit tested. */
internal object DnsLeakAnalysis {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<DnsLeakRecord> {
        return runCatching { json.decodeFromString<List<DnsLeakRecord>>(body) }
            .getOrDefault(emptyList())
    }

    fun resolvers(records: List<DnsLeakRecord>): List<DnsLeakResolver> {
        return records.asSequence()
            .filter { it.type.equals("dns", ignoreCase = true) }
            .filter { it.ip.isNotBlank() }
            .distinctBy { it.ip }
            .map { record ->
                DnsLeakResolver(
                    ip = record.ip,
                    countryId = record.countryId,
                    countryName = record.countryName,
                    asn = record.asn,
                    isp = record.isp,
                )
            }
            .toList()
    }

    /**
     * A small number of distinct resolver ASNs means queries follow one
     * controlled path; many distinct ASNs is the signature of transparent ISP
     * interception or a split DNS setup leaking queries.
     */
    fun verdict(resolvers: List<DnsLeakResolver>): DnsLeakVerdict {
        if (resolvers.isEmpty()) return DnsLeakVerdict.Unknown
        val distinctAsnCount = resolvers.mapNotNull { it.asn }.distinct().size
        return if (distinctAsnCount <= NoLeakMaxDistinctAsn) {
            DnsLeakVerdict.NoLeak
        } else {
            DnsLeakVerdict.SuspectedLeak
        }
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

    private const val NoLeakMaxDistinctAsn = 2
    private const val RegionalIndicatorA = 0x1F1E6
}
