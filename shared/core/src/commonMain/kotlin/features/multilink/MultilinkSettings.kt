// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Link merging: SKIPI Core spreads its new connections over every network link that reaches the internet (Wi-Fi,
 * Ethernet, a phone tether, a phone's mobile data), so many connections get the links' speeds added up. One connection always stays on
 * one link. Links are named by their id, which survives a rename (an adapter's on Desktop, the kind of network on Android).
 */
@Serializable
data class MultilinkSettings(
    val enabled: Boolean = false,
    /** Adapters that take no connections. */
    val excluded: List<String> = emptyList(),
    /** A fixed share per adapter; with none, shares follow each link's measured speed. */
    val weights: Map<String, Double> = emptyMap(),
    /** A cap per adapter, in Mbit/s. */
    val limits: Map<String, Double> = emptyMap(),
    /** Adapters treated as phone hotspots, besides the ones Core recognises by itself. */
    val phones: List<String> = emptyList(),
    /** Holds a phone back after a few minutes of heavy use, so it does not get hot. */
    val phoneCooling: Boolean = false,
    /** What a cooling phone is held to, in Mbit/s; 0 takes Core's default. */
    val coolMbps: Double = 0.0,
    /** Addresses ("ip:port") a link's internet access is checked against; empty takes Core's defaults. */
    val probeTargets: List<String> = emptyList(),
    /** Names the user gave adapters; shown instead of the system's, never sent to Core. */
    val names: Map<String, String> = emptyMap(),
) {
    fun withLinkUsed(id: String, used: Boolean): MultilinkSettings =
        copy(excluded = if (used) excluded - id else (excluded - id) + id)

    fun withLimit(id: String, mbps: Double): MultilinkSettings =
        copy(limits = if (mbps > 0) limits + (id to mbps) else limits - id)

    fun withName(id: String, name: String): MultilinkSettings {
        val trimmed = name.trim()
        return copy(names = if (trimmed.isEmpty()) names - id else names + (id to trimmed))
    }

    fun displayName(id: String, systemName: String): String = names[id] ?: systemName

    /** What Core is given. Merging runs only while a tunnel runs, so an idle SKIPI sends nothing over the links. */
    fun toCoreJson(tunnelRunning: Boolean): String = CoreJson.encodeToString(
        CoreMultilinkSettings.serializer(),
        CoreMultilinkSettings(
            enabled = enabled && tunnelRunning,
            excluded = excluded,
            weights = weights,
            limits = limits,
            phones = phones,
            phoneCooling = phoneCooling,
            coolMbps = coolMbps,
            probeTargets = probeTargets,
        ),
    )
}

/** Core's settings: [MultilinkSettings] without what only the app uses. */
@Serializable
private data class CoreMultilinkSettings(
    val enabled: Boolean,
    val excluded: List<String>,
    val weights: Map<String, Double>,
    val limits: Map<String, Double>,
    val phones: List<String>,
    val phoneCooling: Boolean,
    val coolMbps: Double,
    val probeTargets: List<String>,
)

private val CoreJson = Json { encodeDefaults = true }
private val StatusJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

@Serializable
enum class MultilinkLinkKind {
    @SerialName("wifi") WiFi,
    @SerialName("ethernet") Ethernet,
    @SerialName("phone") Phone,
    /** The device's own mobile data (Android). */
    @SerialName("mobile") Mobile,
    @SerialName("network") Other,
}

/** What Core reports about link merging. */
@Serializable
data class MultilinkStatus(
    val enabled: Boolean = false,
    /** At least two links can take new connections. */
    val merging: Boolean = false,
    val links: List<MultilinkLink> = emptyList(),
    val skipped: List<MultilinkSkipped> = emptyList(),
)

@Serializable
data class MultilinkLink(
    val id: String,
    val name: String,
    val kind: MultilinkLinkKind = MultilinkLinkKind.Other,
    val ipv4: String = "",
    val ipv6: String = "",
    /** "up", "checking", "no internet", or "-" without an address of that family. */
    val state: String = "-",
    val state6: String = "-",
    val excluded: Boolean = false,
    val phone: Boolean = false,
    val weight: Double = 1.0,
    val flows: Long = 0,
    /** What SKIPI moves over the link, in bytes/s. */
    val rateIn: Double = 0.0,
    val rateOut: Double = 0.0,
    /** What every app moves over the link, in bytes/s. */
    val linkIn: Double = 0.0,
    val linkOut: Double = 0.0,
    val limitMbps: Double = 0.0,
    val coolMbps: Double = 0.0,
) {
    /** The link's download speed as a whole, every app included, in Mbit/s. */
    fun mbpsDown(): Double = maxOf(rateIn, linkIn) * BitsPerByte / BitsPerMegabit

    fun mbpsUp(): Double = maxOf(rateOut, linkOut) * BitsPerByte / BitsPerMegabit

    val online: Boolean get() = state == "up" || state6 == "up"
}

@Serializable
data class MultilinkSkipped(val name: String, val reason: String = "")

/** Reads Core's status; anything unreadable is "off", never an error the screen has to show. */
fun parseMultilinkStatus(json: String): MultilinkStatus =
    runCatching { StatusJson.decodeFromString(MultilinkStatus.serializer(), json) }.getOrDefault(MultilinkStatus())

private const val BitsPerByte = 8.0
private const val BitsPerMegabit = 1_000_000.0

/** A speed in Mbit/s with one decimal at most: "12.3", "20". */
fun formatMbps(mbps: Double): String {
    val tenths = kotlin.math.floor(mbps * 10 + 0.5).toLong() // half up, as people round
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}

/** A network link as the Android application reports it to Core, which cannot list the system's networks itself. */
@Serializable
data class MultilinkHostLink(
    val id: String,
    val name: String,
    val kind: MultilinkLinkKind = MultilinkLinkKind.Other,
    val ipv4: String = "",
    val ipv6: String = "",
    /** The system's handle of the network (Android's network handle); Core binds sockets through it. */
    val handle: Long,
    /** Metered links are eased like a phone hotspot. */
    val phone: Boolean = false,
)

fun List<MultilinkHostLink>.toCoreLinksJson(): String =
    CoreJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(MultilinkHostLink.serializer()), this)

private val StoredJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

/** The settings as a client stores them. */
fun encodeMultilinkSettings(settings: MultilinkSettings): String =
    StoredJson.encodeToString(MultilinkSettings.serializer(), settings)

/** Reads what [encodeMultilinkSettings] wrote; anything missing or unreadable is the defaults (merging off). */
fun decodeMultilinkSettings(json: String?): MultilinkSettings =
    if (json.isNullOrBlank()) MultilinkSettings()
    else runCatching { StoredJson.decodeFromString(MultilinkSettings.serializer(), json) }.getOrDefault(MultilinkSettings())

/** One link in the tray menu: [id] to switch it, [label] to show, [used] when it takes connections. */
data class MultilinkTrayEntry(
    val id: String,
    val label: String,
    val used: Boolean,
    val kind: MultilinkLinkKind = MultilinkLinkKind.Other,
)

/** The tray's entries: one per link while merging is on, none otherwise. */
fun MultilinkSettings.trayEntries(status: MultilinkStatus): List<MultilinkTrayEntry> {
    if (!enabled) return emptyList()
    return status.links.map { link ->
        MultilinkTrayEntry(
            id = link.id,
            label = displayName(link.id, link.name) + " · ↓ " + formatMbps(link.mbpsDown()),
            used = !link.excluded && link.id !in excluded,
            kind = link.kind,
        )
    }
}
