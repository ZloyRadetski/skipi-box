// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.customXrayConfigProtocolDisplayName
import features.proxy.server.model.formatCustomXrayConfigJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Result of recognizing and parsing a Custom Xray JSON payload. Platform
 * callers choose how to expose invalid input and rejected individual configs.
 */
sealed interface CustomXrayConfigImportResult {
    /** The input is not a JSON object or array payload. */
    object NotJson : CustomXrayConfigImportResult

    /** The input looks like JSON but cannot be parsed. */
    object InvalidJson : CustomXrayConfigImportResult

    /** A JSON array did not contain any configuration objects. */
    object NoConfigObjects : CustomXrayConfigImportResult

    data class Imported(
        val configCount: Int,
        val servers: List<Custom>,
        val rejectedConfigCount: Int,
    ) : CustomXrayConfigImportResult
}

/**
 * Parses one Custom Xray JSON object or an array of such objects without UI,
 * filesystem or platform logging dependencies.
 */
fun parseCustomXrayConfigPayload(text: String): CustomXrayConfigImportResult {
    val candidate = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    if (!candidate.startsWith('{') && !candidate.startsWith('[')) {
        return CustomXrayConfigImportResult.NotJson
    }

    val root = runCatching {
        ProxyServer.json.parseToJsonElement(candidate)
    }.getOrElse {
        return CustomXrayConfigImportResult.InvalidJson
    }
    val configs = when (root) {
        is JsonObject -> listOf(root)
        is JsonArray -> root.mapNotNull { element -> element as? JsonObject }
        else -> return CustomXrayConfigImportResult.NotJson
    }
    if (configs.isEmpty()) {
        return CustomXrayConfigImportResult.NoConfigObjects
    }

    var rejectedConfigCount = 0
    val servers = configs.mapIndexedNotNull { index, config ->
        runCatching {
            Custom(
                remarks = config.customXrayRemarks(index),
                configJson = formatCustomXrayConfigJson(config),
            ).also { server ->
                require(server.validateBasic().isEmpty()) { "custom Xray config is invalid" }
            }
        }.onFailure {
            rejectedConfigCount += 1
        }.getOrNull()
    }

    return CustomXrayConfigImportResult.Imported(
        configCount = configs.size,
        servers = servers,
        rejectedConfigCount = rejectedConfigCount,
    )
}

private fun JsonObject.customXrayRemarks(index: Int): String =
    string("remarks")
        ?: string("remark")
        ?: string("name")
        ?: string("tag")
        ?: "${customXrayConfigProtocolDisplayName(formatCustomXrayConfigJson(this))} ${index + 1}"

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
