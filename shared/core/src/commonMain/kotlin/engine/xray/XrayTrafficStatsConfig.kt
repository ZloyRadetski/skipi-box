// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Enables Xray's in-memory traffic counters without adding an api.listen
 * endpoint or the gRPC StatsService. SKIPI Core reads these counters directly
 * from the same process.
 */
fun JsonObject.withXrayTrafficStatsConfig(): JsonObject {
    val policy = objectValue("policy") ?: buildJsonObject {}
    return updated {
        put("stats", objectValue("stats") ?: buildJsonObject {})
        put("policy", policy.withTrafficStatsPolicy())
    }
}

fun JsonObjectBuilder.putXrayTrafficStatsConfig(enabled: Boolean) {
    if (!enabled) return
    put("stats", buildJsonObject {})
    put(
        "policy",
        buildJsonObject {
            putTrafficStatsPolicySystem()
        },
    )
}

private fun JsonObject.withTrafficStatsPolicy(): JsonObject {
    val system = objectValue("system") ?: buildJsonObject {}
    return updated {
        put("system", system.updated { putTrafficStatsPolicyFlags() })
    }
}

private fun JsonObjectBuilder.putTrafficStatsPolicySystem() {
    put("system", buildJsonObject { putTrafficStatsPolicyFlags() })
}

private fun JsonObjectBuilder.putTrafficStatsPolicyFlags() {
    put("statsInboundUplink", true)
    put("statsInboundDownlink", true)
    put("statsOutboundUplink", true)
    put("statsOutboundDownlink", true)
}
