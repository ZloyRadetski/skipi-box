// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.hevtun

/** Inputs of the hev-socks5-tunnel YAML; files, executables and process handling stay with the host. */
data class HevSocks5TunnelYamlSpec(
    val mtu: Int,
    val ipv4Address: String,
    val ipv6Address: String?,
    val socksAddress: String,
    val socksPort: Int,
    val logPath: String,
    val tunnelName: String? = null,
    val socksUsername: String = "",
    val socksPassword: String = "",
    val enableMultiQueue: Boolean = false,
    val enableTcpFastOpen: Boolean = false,
    val tcpReadWriteTimeoutMillis: Int = DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis,
    val udpReadWriteTimeoutMillis: Int = DefaultHevSocks5TunnelUdpReadWriteTimeoutMillis,
    val logLevel: String = DefaultHevSocks5TunnelLogLevel,
)

fun buildHevSocks5TunnelConfigYaml(spec: HevSocks5TunnelYamlSpec): String = buildString {
    appendLine("tunnel:")
    spec.tunnelName?.let { name ->
        appendLine("  name: ${name.toYamlSingleQuotedString()}")
    }
    appendLine("  mtu: ${spec.mtu}")
    if (spec.enableMultiQueue) {
        appendLine("  multi-queue: true")
    }
    appendLine("  ipv4: ${spec.ipv4Address.toYamlSingleQuotedString()}")
    spec.ipv6Address?.let { address ->
        appendLine("  ipv6: ${address.toYamlSingleQuotedString()}")
    }
    appendLine("socks5:")
    appendLine("  port: ${spec.socksPort}")
    appendLine("  address: ${spec.socksAddress.toYamlSingleQuotedString()}")
    appendLine("  udp: 'udp'")
    if (spec.socksUsername.isNotBlank()) {
        appendLine("  username: ${spec.socksUsername.toYamlSingleQuotedString()}")
        appendLine("  password: ${spec.socksPassword.toYamlSingleQuotedString()}")
    }
    if (spec.enableTcpFastOpen) {
        appendLine("  tcp-fastopen: true")
    }
    appendLine("misc:")
    appendLine("  tcp-read-write-timeout: ${spec.tcpReadWriteTimeoutMillis}")
    appendLine("  udp-read-write-timeout: ${spec.udpReadWriteTimeoutMillis}")
    appendLine("  log-file: ${spec.logPath.toYamlSingleQuotedString()}")
    appendLine("  log-level: ${spec.logLevel}")
}

private fun String.toYamlSingleQuotedString(): String = "'${replace("'", "''")}'"
