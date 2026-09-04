// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.logs.AndroidAppLogger
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Wireguard
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult

private const val LogTag = "WireguardConfImport"

internal suspend fun parseProxyServersFromWireguardConf(
    text: String,
    @Suppress("UNUSED_PARAMETER") context: ProxyServerImportContext,
): ProxyServerImportResult {
    val clean = text.trimStart(ImportByteOrderMark).trim()
    if (!clean.contains("[Interface]", ignoreCase = true) ||
        !clean.contains("[Peer]", ignoreCase = true)
    ) {
        return EmptyProxyServerImportResult
    }

    return runCatching {
        var currentSection = ""
        val interfaceProps = mutableMapOf<String, String>()
        val peerProps = mutableMapOf<String, String>()
        var foundFirstComment = ""

        clean.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            if (line.startsWith("#") || line.startsWith(";")) {
                if (foundFirstComment.isBlank()) {
                    foundFirstComment = line.trimStart('#', ';', ' ').trim()
                }
                return@forEach
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).trim().lowercase()
                return@forEach
            }
            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = line.substring(0, eqIdx).trim().lowercase()
            val value = line.substring(eqIdx + 1).trim()
            when (currentSection) {
                "interface" -> interfaceProps[key] = value
                "peer" -> peerProps[key] = value
            }
        }

        val privateKey = interfaceProps["privatekey"] ?: ""
        val address = interfaceProps["address"] ?: ""
        val mtu = interfaceProps["mtu"] ?: "1420"

        val publicKey = peerProps["publickey"] ?: ""
        val presharedKey = peerProps["presharedkey"] ?: ""
        val endpoint = peerProps["endpoint"] ?: ""
        val (server, port) = parseEndpointHostPort(endpoint)

        // Check for AmneziaWG obfuscation fields in [Interface]
        val jc = interfaceProps["jc"]
        val jmin = interfaceProps["jmin"]
        val jmax = interfaceProps["jmax"]
        val s1 = interfaceProps["s1"]
        val s2 = interfaceProps["s2"]
        val h1 = interfaceProps["h1"]
        val h2 = interfaceProps["h2"]
        val h3 = interfaceProps["h3"]
        val h4 = interfaceProps["h4"]

        val isAmneziaWg = listOf(jc, jmin, jmax, s1, s2, h1, h2, h3, h4).any { !it.isNullOrBlank() }

        val remarks = foundFirstComment.ifBlank {
            if (isAmneziaWg) "Amnezia WG ($server:$port)" else "WireGuard ($server:$port)"
        }

        val serverObj: ProxyServer<*> = if (isAmneziaWg) {
            AmneziaWg(
                remarks = remarks,
                server = server,
                port = port.ifBlank { "51820" },
                secretKey = privateKey,
                publicKey = publicKey,
                preSharedKey = presharedKey,
                address = address.ifBlank { "10.8.0.2/32" },
                mtu = mtu,
                jc = jc ?: "4",
                jmin = jmin ?: "40",
                jmax = jmax ?: "70",
                s1 = s1 ?: "15",
                s2 = s2 ?: "30",
                h1 = h1.orEmpty(),
                h2 = h2.orEmpty(),
                h3 = h3.orEmpty(),
                h4 = h4.orEmpty(),
            )
        } else {
            Wireguard(
                remarks = remarks,
                server = server,
                port = port.ifBlank { "51820" },
                secretKey = privateKey,
                publicKey = publicKey,
                preSharedKey = presharedKey,
                address = address.ifBlank { "172.16.0.2/32" },
                mtu = mtu,
            )
        }

        val issues = serverObj.validateBasic()
        if (issues.isNotEmpty()) {
            throw IllegalArgumentException("Basic validation failed: ${issues.joinToString { it.error.name }}")
        }

        ProxyServerImportResult(
            urlCount = 1,
            servers = listOf(serverObj),
        )
    }.onFailure { error ->
        AndroidAppLogger.warn(LogTag, "Failed to parse WireGuard / AmneziaWG conf", error)
    }.getOrElse { EmptyProxyServerImportResult }
}

private fun parseEndpointHostPort(endpoint: String): Pair<String, String> {
    if (endpoint.isBlank()) return "" to ""
    val trimmed = endpoint.trim()
    return if (trimmed.startsWith("[")) {
        val closeBracket = trimmed.indexOf(']')
        val host = if (closeBracket > 0) trimmed.substring(1, closeBracket) else trimmed
        val port = if (closeBracket > 0 && trimmed.length > closeBracket + 2 && trimmed[closeBracket + 1] == ':') {
            trimmed.substring(closeBracket + 2)
        } else ""
        host to port
    } else {
        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon > 0) {
            trimmed.substring(0, lastColon) to trimmed.substring(lastColon + 1)
        } else {
            trimmed to ""
        }
    }
}
