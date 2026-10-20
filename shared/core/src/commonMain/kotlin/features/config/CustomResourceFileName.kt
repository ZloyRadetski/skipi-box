// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** Android-compatible filename sanitizing for custom resources. */
fun sanitizeCustomResourceFileName(value: String, fallback: String): String {
    val candidate = value
        .trim()
        .replace('\\', '/')
        .substringAfterLast('/')
        .map { char -> if (char.isResourceFileNameChar()) char else '_' }
        .joinToString("")
        .trim()
    return candidate
        .takeUnless { it.isBlank() || it == "." || it == ".." }
        ?: fallback
}

/** Returns the value when it passes the Android custom-resource filename checks. */
fun customResourceFileNameOrNull(value: String): String? {
    if (value.isBlank() || value != value.trim()) return null
    if (value.any { char -> char.isWhitespace() || char == ':' }) return null
    return sanitizeCustomResourceFileName(value, fallback = "")
        .takeIf { sanitized -> sanitized == value }
}

private fun Char.isResourceFileNameChar(): Boolean {
    return code >= 32 && this != '/' && this != '\\'
}
