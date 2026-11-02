// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.ProxyServer
import utils.decodeFlexibleBase64OrNull

internal const val VpnLinkScheme = "vpn"

/**
 * Reads a `vpn://<Base64>` link whose payload is a whole AmneziaWG or WireGuard `.conf`.
 * Standard and URL-safe Base64 are accepted, with or without padding; a `#fragment` is ignored.
 */
internal fun parseVpnConfLink(link: String): ProxyServer<*> {
    val payload = link.substringAfter("://").substringBefore('#')
    val conf = requireNotNull(payload.decodeFlexibleBase64OrNull()) { "vpn link payload is not Base64" }
        .decodeToString()
    return when (val result = parseWireguardConf(conf)) {
        is WireguardConfParseResult.Imported -> result.server
        is WireguardConfParseResult.Invalid -> throw result.error
        WireguardConfParseResult.NotWireguardConf -> throw IllegalArgumentException("vpn link does not contain a WireGuard config")
    }
}
