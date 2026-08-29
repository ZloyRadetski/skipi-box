// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

const val V2RayTransportRaw = "raw"
const val V2RayTransportMkcp = "mkcp"
const val V2RayTransportWebSocket = "websocket"
const val V2RayTransportHttpUpgrade = "httpupgrade"
const val V2RayTransportXhttp = "xhttp"
const val V2RayTransportGrpc = "grpc"

data class V2RayTransportOption(
    val value: String,
) {
    val label: String get() = value
}

val V2RayTransportOptions = listOf(
    V2RayTransportOption(V2RayTransportRaw),
    V2RayTransportOption(V2RayTransportMkcp),
    V2RayTransportOption(V2RayTransportWebSocket),
    V2RayTransportOption(V2RayTransportHttpUpgrade),
    V2RayTransportOption(V2RayTransportXhttp),
    V2RayTransportOption(V2RayTransportGrpc),
)

fun String?.toCanonicalV2RayTransportType(defaultType: String = V2RayTransportRaw): String {
    val normalized = this
        ?.trim()
        ?.lowercase()
        .orEmpty()
        .ifBlank { defaultType }
    return when (normalized) {
        "tcp", V2RayTransportRaw -> V2RayTransportRaw
        "kcp", V2RayTransportMkcp -> V2RayTransportMkcp
        "ws", V2RayTransportWebSocket -> V2RayTransportWebSocket
        V2RayTransportHttpUpgrade -> V2RayTransportHttpUpgrade
        "splithttp", V2RayTransportXhttp -> V2RayTransportXhttp
        V2RayTransportGrpc -> V2RayTransportGrpc
        else -> normalized
    }
}

fun String.toV2RayTransportUrlType(): String {
    val canonicalType = toCanonicalV2RayTransportType()
    return when (canonicalType) {
        V2RayTransportMkcp -> "kcp"
        V2RayTransportWebSocket -> "ws"
        else -> canonicalType
    }
}

fun v2RayTransportOptionIndex(type: String?): Int {
    val canonicalType = type.toCanonicalV2RayTransportType()
    val index = V2RayTransportOptions.indexOfFirst { option -> option.value == canonicalType }
    return if (index >= 0) index else 0
}
