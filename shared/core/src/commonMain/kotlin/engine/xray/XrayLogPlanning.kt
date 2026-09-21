// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class XrayLogConfigRequest(
    val coreLogLevel: Int,
    val enableAccessLog: Boolean,
    val accessLogPath: String,
    val errorLogPath: String,
)

fun buildXrayLogConfig(request: XrayLogConfigRequest): JsonObject = buildJsonObject {
    put("loglevel", xrayLogLevel(request.coreLogLevel))
    // DNS entries are useful only while explicitly diagnosing a DNS path.
    put("dnsLog", request.coreLogLevel == 0)
    put(
        "access",
        if (request.enableAccessLog) {
            request.accessLogPath.ifBlank { XrayLogDisabled }
        } else {
            XrayLogDisabled
        },
    )
    put("error", request.errorLogPath)
}

fun xrayLogLevel(coreLogLevel: Int): String = when (coreLogLevel) {
    0 -> "debug"
    1 -> "info"
    2 -> "warning"
    3 -> "error"
    4 -> XrayLogDisabled
    else -> "warning"
}
