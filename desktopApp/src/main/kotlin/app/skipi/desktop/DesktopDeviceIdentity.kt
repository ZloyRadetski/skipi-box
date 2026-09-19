// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object DesktopDeviceIdentity {
    private const val SubscriptionHwidMacAlgorithm = "HmacSHA256"
    private const val SubscriptionHwidSalt = "SKIPI::subscription-hwid::v1::Radetski"
    private const val SubscriptionHwidPayloadSeparator = ":"
    const val ClientName = "SKIPI"
    const val ClientVersion = "0.4.1"

    fun getOrGenerateInstallationUuid(existingUuid: String?): String {
        return existingUuid?.trim()?.takeIf(String::isNotEmpty) ?: UUID.randomUUID().toString()
    }

    /**
     * Reads a persistent hardware/machine identity on Linux or Windows,
     * falling back to local host and user details.
     */
    fun getHardwareIdentifier(): String {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (osName.contains("linux")) {
            val systemdMachineId = Path.of("/etc/machine-id")
            if (Files.isRegularFile(systemdMachineId)) {
                runCatching { Files.readString(systemdMachineId, StandardCharsets.UTF_8).trim() }
                    .getOrNull()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { return it }
            }
            val dbusMachineId = Path.of("/var/lib/dbus/machine-id")
            if (Files.isRegularFile(dbusMachineId)) {
                runCatching { Files.readString(dbusMachineId, StandardCharsets.UTF_8).trim() }
                    .getOrNull()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { return it }
            }
        } else if (osName.contains("windows")) {
            runCatching {
                val process = ProcessBuilder("reg", "query", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                process.waitFor()
                val match = Regex("""MachineGuid\s+REG_SZ\s+([a-zA-Z0-9-]+)""").find(output)
                match?.groupValues?.getOrNull(1)?.trim()
            }.getOrNull()?.takeIf(String::isNotEmpty)?.let { return it }
        }

        val username = System.getProperty("user.name").orEmpty()
        val hostname = runCatching { InetAddress.getLocalHost().hostName }.getOrNull().orEmpty()
        return "$username@$hostname".ifBlank { "desktop-unknown-host" }
    }

    /**
     * Generates a 64-character lowercase hex HWID from the machine identifier,
     * OS details, and persistent installation UUID using HMAC-SHA256.
     */
    fun computeHwid(installationUuid: String): String {
        val machineId = getHardwareIdentifier()
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty()
        val payload = listOf(
            machineId,
            osName,
            osArch,
            installationUuid.trim(),
        ).joinToString(SubscriptionHwidPayloadSeparator)

        val mac = Mac.getInstance(SubscriptionHwidMacAlgorithm).apply {
            init(
                SecretKeySpec(
                    SubscriptionHwidSalt.toByteArray(StandardCharsets.UTF_8),
                    SubscriptionHwidMacAlgorithm,
                ),
            )
        }
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    /**
     * Prepares standard SKIPI device headers for subscription updates.
     */
    fun deviceHeaders(installationUuid: String): Map<String, String> {
        val hwid = computeHwid(installationUuid)
        val osName = System.getProperty("os.name").orEmpty().ifBlank { "Desktop" }
        val osVersion = System.getProperty("os.version").orEmpty().ifBlank { "unknown" }
        val osArch = System.getProperty("os.arch").orEmpty().ifBlank { "unknown" }

        return linkedMapOf(
            "x-client" to ClientName,
            "x-app-version" to ClientVersion,
            "x-device-os" to osName,
            "x-ver-os" to osVersion,
            "x-device-model" to osArch,
            "x-hwid" to hwid,
            "X-Device-ID" to hwid,
        )
    }
}
