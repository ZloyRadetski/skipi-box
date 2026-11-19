// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Core detaches a hung Xray close this long after stop begins, before Kotlin's own step deadline gives up. */
internal const val CoreStopTimeoutMillis = NativeStopStepDeadlineMillis - 500L

/**
 * The one-call start plan for CoreController.startSession: Core starts
 * AmneziaWG (when present), waits for its SOCKS listener, then Xray.
 */
internal fun buildCoreSessionPlanJson(
    xrayConfigJson: String,
    amneziaWgConfigJson: String?,
    amneziaWgSocksPort: Int,
    connectionLog: Boolean,
): String {
    val plan: JsonObject = buildJsonObject {
        put("xrayConfig", JsonPrimitive(xrayConfigJson))
        if (!amneziaWgConfigJson.isNullOrBlank() && amneziaWgSocksPort > 0) {
            put("amneziaWg", buildJsonObject {
                put("config", JsonPrimitive(amneziaWgConfigJson))
                put("socksPort", JsonPrimitive(amneziaWgSocksPort))
            })
        }
        put("connectionLog", JsonPrimitive(connectionLog))
    }
    return Json.encodeToString(JsonObject.serializer(), plan)
}

/** Core instances whose close never returned still hold sockets; a non-null reason means the runtime is dirty. */
internal fun coreAbandonedDirtyReason(abandonedInstances: Long): String? =
    if (abandonedInstances > 0) "SKIPI Core abandoned $abandonedInstances instance(s) whose close never returned" else null
