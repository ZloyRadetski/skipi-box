// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

/**
 * Sends the system's own DNS servers down [systemPath] - the way the OS resolver would reach them -
 * and every other server through [otherwise]. A system server is typically a LAN or ISP address that
 * means nothing at the far end of a proxy, so asking it through the tunnel route would silently
 * drop exactly the path a DNS leak test exists to examine.
 */
class SystemPathDnsTxtResolver(
    systemServers: Collection<String>,
    private val systemPath: DnsTxtResolver,
    private val otherwise: DnsTxtResolver,
) : DnsTxtResolver {
    private val systemServers = systemServers.map(String::trim).filter(String::isNotBlank).toSet()

    override suspend fun queryTxt(server: String, domain: String): List<String>? =
        if (server.trim() in systemServers) systemPath.queryTxt(server, domain) else otherwise.queryTxt(server, domain)
}
