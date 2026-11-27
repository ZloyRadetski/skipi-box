// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

private val LoggableLinkHosts = setOf(
    "add", "import", "routing", "conf", "theme", "connect", "open", "disconnect", "close", "toggle",
)
private val LoggableConfigVerbs = setOf("add", "onadd")

/**
 * A link as it may be written to a log: the command only. Everything after it can hold a subscription token,
 * server credentials or a whole config, so only known words are kept and the rest is replaced.
 */
fun redactSkipiLinkForLog(rawUri: String): String {
    val prefix = "skipi://"
    if (!rawUri.startsWith(prefix, ignoreCase = true)) return "$prefix…"
    val rest = rawUri.substring(prefix.length)
    val hostEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
    val host = rest.substring(0, hostEnd).lowercase()
    if (host !in LoggableLinkHosts) return "$prefix…"
    var kept = "$prefix$host"
    var tail = rest.substring(hostEnd)
    if ((host == "conf" || host == "routing") && tail.startsWith("/")) {
        val verb = tail.substring(1).substringBefore('/').lowercase()
        if (verb in LoggableConfigVerbs) {
            kept += "/$verb"
            tail = tail.substring(1 + verb.length)
        }
    }
    return if (tail.isEmpty() || tail == "/") kept else "$kept/…"
}
