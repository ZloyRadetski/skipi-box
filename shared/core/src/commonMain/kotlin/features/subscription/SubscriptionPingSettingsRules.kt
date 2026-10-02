// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** Validation shared by the subscription ping editor and platform adapters. */
object SubscriptionPingSettingsRules {
    fun isValidHttpUrl(value: String): Boolean {
        val input = value.trim()
        val separator = input.indexOf("://")
        if (separator <= 0) return false
        val scheme = input.substring(0, separator)
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false
        val authority = input.substring(separator + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isBlank() || authority.any(Char::isWhitespace)) return false
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isBlank()) return false
        val host: String
        val port: String?
        if (hostPort.startsWith('[')) {
            val closingBracket = hostPort.indexOf(']')
            if (closingBracket <= 1) return false
            host = hostPort.substring(1, closingBracket)
            val suffix = hostPort.substring(closingBracket + 1)
            if (suffix.isNotEmpty() && !suffix.startsWith(':')) return false
            port = suffix.takeIf { it.isNotEmpty() }?.drop(1)
        } else {
            if (hostPort.count { it == ':' } > 1) return false
            host = hostPort.substringBefore(':')
            port = hostPort.substringAfter(':', "").takeIf { ':' in hostPort }
        }
        if (host.isBlank()) return false
        if (host.any { !(it.isLetterOrDigit() || it == '.' || it == '-' || it == ':') }) return false
        if (port != null && (port.isEmpty() || port.any { !it.isDigit() } || port.toIntOrNull() !in 1..65535)) return false
        return true
    }

    fun isValidTimeoutMillis(value: String): Boolean = value.toIntOrNull() in 500..60_000
}
