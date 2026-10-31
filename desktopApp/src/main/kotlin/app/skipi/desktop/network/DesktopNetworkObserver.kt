// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.networkautomation.policy.NetworkAutomationAction
import features.networkautomation.policy.NetworkAutomationDecision
import features.networkautomation.policy.NetworkAutomationPolicy
import features.networkautomation.policy.NetworkAutomationPolicyInput
import features.networkautomation.policy.NetworkObservationPlan
import features.networkautomation.policy.NetworkReconcileInput
import features.networkautomation.policy.NetworkAutomationReconciler
import features.networkautomation.policy.ObservedNetwork
import features.networkautomation.policy.ObservedNetworkTransport
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

/** Plain facts about one OS network adapter, independent of `java.net` for testing. */
internal data class DesktopNetworkInterface(
    val name: String,
    val displayName: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val hasUsableAddress: Boolean,
)

private val virtualAdapterMarkers = listOf(
    "tun", "tap", "wintun", "wireguard", "docker", "veth", "virbr", "vmnet", "vboxnet", "br-", "utun",
    "hyper-v", "vethernet", "virtual", "loopback", "pseudo", "vpn", "skipi",
)

private fun DesktopNetworkInterface.isVirtual(): Boolean {
    val haystack = "$name $displayName".lowercase()
    return virtualAdapterMarkers.any { marker -> haystack.contains(marker) }
}

private fun DesktopNetworkInterface.transport(): ObservedNetworkTransport {
    val lower = name.lowercase()
    val display = displayName.lowercase()
    return when {
        lower.startsWith("wl") || lower.startsWith("wifi") || lower.startsWith("wi-fi") ||
            display.contains("wi-fi") || display.contains("wifi") || display.contains("wireless") ||
            display.contains("802.11") -> ObservedNetworkTransport.WIFI
        lower.startsWith("wwan") || lower.startsWith("ppp") || lower.startsWith("rmnet") ||
            display.contains("mobile broadband") || display.contains("cellular") -> ObservedNetworkTransport.CELLULAR
        else -> ObservedNetworkTransport.ETHERNET
    }
}

/**
 * Desktop counterpart of Android's "active physical network": only up, addressed, non-virtual
 * adapters count, and Wi-Fi is asked for its SSID only when it is actually present.
 */
internal fun observeDesktopNetwork(
    interfaces: List<DesktopNetworkInterface>,
    ssidProvider: () -> String?,
): ObservedNetwork? {
    val physical = interfaces.filter { nic -> nic.isUp && !nic.isLoopback && nic.hasUsableAddress && !nic.isVirtual() }
    if (physical.isEmpty()) return null
    val transports = physical.mapTo(linkedSetOf()) { nic -> nic.transport() }
    val ssid = if (ObservedNetworkTransport.WIFI in transports) ssidProvider() else null
    return ObservedNetwork(transports = transports, wifiSsid = ssid)
}

/** Reads the live adapters from the JVM. */
internal fun readDesktopNetworkInterfaces(): List<DesktopNetworkInterface> =
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList().map { nic ->
            DesktopNetworkInterface(
                name = nic.name.orEmpty(),
                displayName = nic.displayName.orEmpty(),
                isUp = nic.isUp,
                isLoopback = nic.isLoopback,
                hasUsableAddress = nic.inetAddresses.toList().any { address ->
                    !address.isLoopbackAddress && !address.isLinkLocalAddress &&
                        (address is Inet4Address || address is Inet6Address)
                },
            )
        }
    }.getOrDefault(emptyList())

/** `netsh wlan show interfaces` -> SSID of the connected interface. */
internal fun parseNetshWlanSsid(output: String): String? {
    val lines = output.lines().map { it.trim() }
    val connected = lines.any { line ->
        line.substringBefore(':').trim().equals("State", ignoreCase = true) &&
            line.substringAfter(':').trim().equals("connected", ignoreCase = true)
    }
    if (!connected) return null
    return lines.firstOrNull { line -> line.substringBefore(':').trim().equals("SSID", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.takeIf(String::isNotEmpty)
}

/** `nmcli -t -f active,ssid dev wifi` -> SSID of the active access point (`\:` escapes a colon). */
internal fun parseNmcliActiveSsid(output: String): String? =
    output.lines().firstOrNull { it.startsWith("yes:") }
        ?.removePrefix("yes:")?.replace("\\:", ":")?.trim()?.takeIf(String::isNotEmpty)

private fun runCommand(vararg command: String): String? = runCatching {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
    text
}.getOrNull()

/** Current Wi-Fi name via the OS tools (Windows `netsh`, Linux `nmcli`); null when unavailable. */
internal fun readDesktopWifiSsid(): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        "win" in os -> runCommand("netsh", "wlan", "show", "interfaces")?.let(::parseNetshWlanSsid)
        "linux" in os -> runCommand("nmcli", "-t", "-f", "active,ssid", "dev", "wifi")?.let(::parseNmcliActiveSsid)
        else -> null
    }
}

internal fun desktopNetworkObservationPlan(settings: DesktopAppSettings): NetworkObservationPlan =
    NetworkAutomationPolicy.observationPlan(
        networkAutomationEnabled = settings.enableNetworkAutomation,
        onDemandVpnEnabled = settings.enableOnDemandVpn,
        rules = settings.networkAutomationRules,
    )

internal data class DesktopNetworkAutomationStep(val networkId: String, val action: NetworkAutomationAction)

/** One poll of Android's automation: policy picks the rule action, the reconciler turns it into a VPN command. */
internal fun evaluateDesktopNetworkAutomation(
    settings: DesktopAppSettings,
    availableServerIds: Set<Int>,
    selectedServerId: Int?,
    vpnRunning: Boolean,
    previousNetworkId: String?,
    observed: ObservedNetwork?,
): DesktopNetworkAutomationStep {
    val networkId = NetworkAutomationPolicy.networkIdentifier(observed)
    val decision = NetworkAutomationPolicy.evaluate(
        NetworkAutomationPolicyInput(
            networkAutomationEnabled = settings.enableNetworkAutomation,
            onDemandVpnEnabled = settings.enableOnDemandVpn,
            rules = settings.networkAutomationRules.filter { it.enabled },
            network = observed,
            availableServerIds = availableServerIds,
        ),
    )
    val action = NetworkAutomationReconciler.reconcile(
        NetworkReconcileInput(
            networkAutomationEnabled = settings.enableNetworkAutomation,
            onDemandVpnEnabled = settings.enableOnDemandVpn,
            previousNetworkId = previousNetworkId,
            currentNetworkId = networkId,
            vpnRunning = vpnRunning,
            decision = decision,
            selectedServerId = selectedServerId,
            activeProfileChangeNeeded = false,
        ),
    )
    return DesktopNetworkAutomationStep(networkId, action)
}
