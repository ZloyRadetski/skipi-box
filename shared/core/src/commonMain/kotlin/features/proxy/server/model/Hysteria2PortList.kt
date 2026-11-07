// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

/**
 * The Hysteria2 link format allows a port list in the address: `hy2://pass@host:443,20000-29999/`. A URL parser
 * rejects that as a port, so the ports after the first one move to the `mport` query parameter, which is where
 * the rest of the app reads port hopping from. An `mport` or `ports` already in the query wins.
 */
internal fun String.moveHysteriaPortListToQuery(): String {
    val schemeEnd = indexOf("://")
    if (schemeEnd < 0) return this
    val scheme = substring(0, schemeEnd).lowercase()
    if (scheme != ProxyServerConstants.PROTOCOL_HY2 && scheme != ProxyServerConstants.PROTOCOL_HYSTERIA2) return this
    val authorityStart = schemeEnd + 3
    val authorityEnd = indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) length else it }
    val authority = substring(authorityStart, authorityEnd)
    val hostPortStart = authority.lastIndexOf('@') + 1
    val hostPort = authority.substring(hostPortStart)
    val comma = hostPort.indexOf(',')
    if (comma < 0) return this
    val extraPorts = hostPort.substring(comma + 1).trim()
    val cleanedAuthority = authority.substring(0, hostPortStart) + hostPort.substring(0, comma)
    val head = substring(0, authorityStart) + cleanedAuthority
    val tail = substring(authorityEnd)
    if (extraPorts.isEmpty() || tail.hasQueryParameter("mport") || tail.hasQueryParameter("ports")) return head + tail

    val fragmentStart = tail.indexOf('#').let { if (it < 0) tail.length else it }
    val beforeFragment = tail.substring(0, fragmentStart)
    val fragment = tail.substring(fragmentStart)
    val withPorts = when {
        '?' in beforeFragment -> beforeFragment.replaceFirst("?", "?mport=$extraPorts&")
        beforeFragment.isEmpty() -> "/?mport=$extraPorts"
        else -> "$beforeFragment?mport=$extraPorts"
    }
    return head + withPorts + fragment
}

private fun String.hasQueryParameter(name: String): Boolean {
    val query = substringAfter('?', "").substringBefore('#')
    return query.split('&').any { it.substringBefore('=') == name }
}
