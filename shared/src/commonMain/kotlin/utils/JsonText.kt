// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlinx.serialization.json.Json

fun String.formatJsonText(): String {
    val element = JsonText.parseToJsonElement(trim())
    return PrettyJsonText.encodeToString(element)
}

private val JsonText = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val PrettyJsonText = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}
