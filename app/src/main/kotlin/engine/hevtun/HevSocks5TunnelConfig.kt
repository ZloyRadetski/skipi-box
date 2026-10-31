// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.hevtun

import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import engine.xray.XrayCoreLogPaths
import engine.xray.logDirectoryPath
import features.resources.runtime.writeAtomically
import java.io.File

internal data class HevSocks5TunnelConfig(
    val executablePath: String = "",
    val configPath: String,
    val pidPath: String = "",
    val logPath: String,
    val socksAddress: String,
    val socksPort: Int,
    val socksUsername: String = "",
    val socksPassword: String = "",
    val mtu: Int,
    val ipv4Address: String,
    val ipv6Address: String?,
    val tunnelName: String? = null,
    val enableMultiQueue: Boolean = false,
    val enableTcpFastOpen: Boolean = false,
    val tcpReadWriteTimeoutMillis: Int = DefaultHevSocks5TunnelTcpReadWriteTimeoutMillis,
    val udpReadWriteTimeoutMillis: Int = DefaultHevSocks5TunnelUdpReadWriteTimeoutMillis,
    val logLevel: String = DefaultHevSocks5TunnelLogLevel,
)

internal fun HevSocks5TunnelConfig.writeConfigFile() {
    File(configPath).parentFile?.mkdirs()
    if (logPath.isNotBlank()) {
        File(logPath).parentFile?.mkdirs()
    }
    writeAtomically(File(configPath)) { output ->
        output.write(buildHevSocks5TunnelConfigYaml().toByteArray(Charsets.UTF_8))
    }
}

private fun HevSocks5TunnelConfig.buildHevSocks5TunnelConfigYaml(): String = buildHevSocks5TunnelConfigYaml(
    HevSocks5TunnelYamlSpec(
        mtu = mtu,
        ipv4Address = ipv4Address,
        ipv6Address = ipv6Address,
        socksAddress = socksAddress,
        socksPort = socksPort,
        logPath = logPath,
        tunnelName = tunnelName,
        socksUsername = socksUsername,
        socksPassword = socksPassword,
        enableMultiQueue = enableMultiQueue,
        enableTcpFastOpen = enableTcpFastOpen,
        tcpReadWriteTimeoutMillis = tcpReadWriteTimeoutMillis,
        udpReadWriteTimeoutMillis = udpReadWriteTimeoutMillis,
        logLevel = logLevel,
    ),
)

internal fun hevSocks5TunnelSocksTargetAddress(localProxyOptions: LocalProxyOptions): String {
    return if (localProxyOptions.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
        LocalProxyLoopbackAddress
    } else {
        localProxyOptions.listenAddress
    }
}

internal fun XrayCoreLogPaths.hevSocks5TunnelLogFile(fileName: String): File {
    return File(logDirectoryPath(), fileName)
}

