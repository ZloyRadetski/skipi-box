// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import engine.network.isIpv4Address
import engine.network.isIpv6Address
import engine.network.toPortOrNull

/** One link's settings as the user edits them; every field is text, and an empty one means "not set". */
data class MultilinkLinkDraft(
    val name: String = "",
    val limitMbps: String = "",
    val weight: String = "",
    val phone: Boolean = false,
) {
    companion object {
        fun of(settings: MultilinkSettings, id: String) = MultilinkLinkDraft(
            name = settings.names[id].orEmpty(),
            limitMbps = settings.limits[id]?.let(::plainNumber).orEmpty(),
            weight = settings.weights[id]?.let(::plainNumber).orEmpty(),
            phone = id in settings.phones,
        )
    }
}

/** Why a draft was refused. */
enum class MultilinkDraftError { Limit, Weight }

class MultilinkDraftException(val error: MultilinkDraftError) : IllegalArgumentException(error.name)

/** The settings with [id] set as [draft] says; a field that is not a sensible number refuses the whole draft. */
fun MultilinkSettings.withDraft(id: String, draft: MultilinkLinkDraft): Result<MultilinkSettings> = runCatching {
    val limit = draft.limitMbps.number(MultilinkDraftError.Limit)
    if (limit != null && (limit < 0 || limit > MaxLimitMbps)) throw MultilinkDraftException(MultilinkDraftError.Limit)
    val weight = draft.weight.number(MultilinkDraftError.Weight)
    if (weight != null && (weight <= 0 || weight > MaxWeight)) throw MultilinkDraftException(MultilinkDraftError.Weight)
    withName(id, draft.name)
        .withLimit(id, limit ?: 0.0)
        .copy(
            weights = if (weight == null) weights - id else weights + (id to weight),
            phones = if (draft.phone) (phones - id) + id else phones - id,
        )
}

/** Reads "ip:port" addresses separated by commas or lines; an IPv6 address goes in brackets. */
fun parseProbeTargets(text: String): Result<List<String>> = runCatching {
    text.split(',', '\n', ';').map(String::trim).filter(String::isNotEmpty).map { target ->
        require(isProbeTarget(target)) { "\"$target\" is not an ip:port" }
        target
    }
}

fun MultilinkSettings.probeTargetsText(): String = probeTargets.joinToString(", ")

private fun isProbeTarget(target: String): Boolean {
    val portStart = target.lastIndexOf(':')
    if (portStart <= 0) return false
    val host = target.substring(0, portStart)
    val port = target.substring(portStart + 1).toPortOrNull() ?: return false
    if (port == 0) return false
    return if (host.startsWith('[') && host.endsWith(']')) {
        isIpv6Address(host.substring(1, host.length - 1))
    } else {
        isIpv4Address(host)
    }
}

private fun String.number(error: MultilinkDraftError): Double? {
    val text = trim().replace(',', '.')
    if (text.isEmpty()) return null
    val value = text.toDoubleOrNull() ?: throw MultilinkDraftException(error)
    if (value.isNaN() || value.isInfinite()) throw MultilinkDraftException(error)
    return value
}

private fun plainNumber(value: Double): String =
    if (value == kotlin.math.floor(value) && value < Long.MAX_VALUE) value.toLong().toString() else value.toString()

private const val MaxLimitMbps = 1_000_000.0
private const val MaxWeight = 1_000.0
