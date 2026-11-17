// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val ErrorJson = Json { isLenient = false }
private val MessageKeys = listOf("message", "error", "detail", "msg", "description", "title", "reason", "error_description")
private val HtmlTitle = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)

/**
 * The text a subscription server wrote about a failed request: a JSON error field, an HTML page's title, or a
 * short plain line. Null when the body holds nothing worth showing.
 */
fun parseSubscriptionHttpErrorMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null
    val trimmed = errorBody.trim()

    val jsonMessage = runCatching {
        val root = ErrorJson.parseToJsonElement(trimmed) as? JsonObject ?: return@runCatching null
        for (key in MessageKeys) {
            when (val value = root[key]) {
                is JsonPrimitive -> if (value.isString && !value.contentOrNull.isNullOrBlank()) return@runCatching value.content.trim()
                is JsonObject -> {
                    val inner = (value["message"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                        ?: (value["detail"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                    if (inner != null) return@runCatching inner.trim()
                }
                else -> Unit
            }
        }
        null
    }.getOrNull()
    if (!jsonMessage.isNullOrBlank()) return jsonMessage.take(200)

    if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
        val title = HtmlTitle.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        return title?.takeIf(String::isNotBlank)?.take(100)
    }

    if (trimmed.length <= 250 && !trimmed.contains("<html", ignoreCase = true)) {
        return trimmed.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(150)
    }
    return null
}
