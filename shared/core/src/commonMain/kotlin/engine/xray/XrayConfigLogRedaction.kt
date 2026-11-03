// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val Masked = "***"

private val SensitiveKeys = setOf(
    "id", "uuid", "password", "pass", "user", "username", "auth", "token", "secret",
    "privatekey", "publickey", "shortid", "secretkey", "presharedkey", "psk",
    "address", "servername", "sni", "host", "obfspassword", "obfs-password",
    "email", "ip", "hosts",
)

private val prettyJson = Json { prettyPrint = true }

/**
 * Renders an Xray config for the app log without credentials or server
 * identity. Logs are kept on the device and shown in the in-app viewer, so the
 * raw config (UUIDs, passwords, hostnames) must never reach them.
 */
fun redactXrayConfigForLog(configJson: String): String {
    val parsed = runCatching { Json.parseToJsonElement(configJson) }.getOrNull()
        ?: return "<config not shown: unparseable>"
    return prettyJson.encodeToString(JsonElement.serializer(), parsed.redacted(parentSensitive = false))
}

private fun JsonElement.redacted(parentSensitive: Boolean): JsonElement = when (this) {
    is JsonObject -> JsonObject(
        mapValues { (key, value) -> value.redacted(parentSensitive = key.lowercase() in SensitiveKeys) },
    )
    is JsonArray -> JsonArray(map { it.redacted(parentSensitive) })
    is JsonPrimitive -> if (parentSensitive && isString) JsonPrimitive(Masked) else this
}
