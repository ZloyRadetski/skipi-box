// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.Masque
import io.ktor.http.Url

/** The SNI Mihomo uses when a `masque` node has none (transport/masque ConnectSNI). */
private const val MihomoMasqueDefaultSni = "consumer-masque.cloudflareclient.com"

/**
 * Mihomo's `type: masque` is a WARP-style CONNECT-IP profile (adapter/outbound/masque.go): ECDSA keys,
 * local addresses and an optional connect `uri`. Mihomo-only tuning (`udp`, `mtu`, congestion
 * control, `ip-stack`, timeouts) has no Xray counterpart and is ignored; settings that change what
 * the connection is are refused instead of being dropped.
 */
fun MihomoYamlMap.toMihomoMasqueProxyServer(): Masque {
    if (boolean("skip-cert-verify") == true) {
        unsupported("skip-cert-verify is not supported")
    }
    val network = string("network").orEmpty()
    if (network.isNotBlank() && !network.equals("h3", ignoreCase = true)) {
        // h2 and h3-l4proxy are other transports of Mihomo, not the CONNECT-IP over HTTP/3 of the core
        unsupported("MASQUE network $network is not supported")
    }
    val addresses = listOfNotNull(string("ip"), string("ipv6"))
    if (addresses.isEmpty()) {
        unsupported("MASQUE profile needs ip or ipv6")
    }
    val (host, path) = string("uri")?.toMasqueHostAndPath() ?: ("" to "")
    return Masque(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = requiredString("port"),
        sni = string("sni") ?: MihomoMasqueDefaultSni,
        host = host,
        path = path,
        // Mihomo only uses `dns` for remote resolving
        remoteDns = if (boolean("remote-dns-resolve") == true) csvString("dns").orEmpty() else "",
        warpPrivateKey = requiredString("private-key"),
        warpPublicKey = requiredString("public-key"),
        warpAddress = addresses.joinToString(","),
    )
}

/** The core takes the connect URI as `host` and `path`; the path "/" is its WARP default, so it stays blank. */
private fun String.toMasqueHostAndPath(): Pair<String, String> {
    val url = runCatching { Url(this) }.getOrNull()
        ?.takeIf { it.host.isNotBlank() }
        ?: unsupported("MASQUE uri is invalid")
    val host = if (url.specifiedPort > 0) "${url.host}:${url.specifiedPort}" else url.host
    val query = url.encodedQuery.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
    val path = (url.encodedPath + query).takeUnless { it.isBlank() || it == "/" }.orEmpty()
    return host to path
}
