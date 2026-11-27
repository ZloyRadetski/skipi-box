// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

/** How a network reaches the internet, as far as link merging cares. */
enum class HostTransport { WiFi, Cellular, Ethernet }

/**
 * One network the host system reports (Android's ConnectivityManager), reduced to what link merging needs. A network
 * that is a VPN, has no internet or has none of these transports is left out by the caller.
 * [addresses] are textual IP addresses (IPv6 may carry a "%scope").
 */
data class HostNetworkSnapshot(
    val handle: Long,
    val transport: HostTransport,
    val metered: Boolean,
    val interfaceName: String,
    val addresses: List<String>,
)

/**
 * The links link merging can spread connections over. The id outlives a reconnect (a new network every time), so
 * settings stay with the link; a second network of the same kind is told apart by its interface, and the networks are
 * taken in interface order so the same two networks always get the same ids.
 */
fun buildHostLinks(snapshots: List<HostNetworkSnapshot>, mobileName: String = "Mobile"): List<MultilinkHostLink> {
    val used = HashSet<String>()
    val out = ArrayList<MultilinkHostLink>()
    val ordered = snapshots.sortedWith(compareBy<HostNetworkSnapshot> { it.interfaceName }.thenBy { it.handle })
    for (network in ordered) {
        val ipv4 = network.addresses.firstOrNull(::isUsableIpv4)
        val ipv6 = network.addresses.firstOrNull(::isGlobalIpv6)
        if (ipv4 == null && ipv6 == null) continue
        val (baseId, kind, baseName) = when (network.transport) {
            HostTransport.WiFi -> Triple("wifi", MultilinkLinkKind.WiFi, "Wi-Fi")
            HostTransport.Cellular -> Triple("mobile", MultilinkLinkKind.Mobile, mobileName)
            HostTransport.Ethernet -> Triple("ethernet", MultilinkLinkKind.Ethernet, "Ethernet")
        }
        val id = if (used.add(baseId)) baseId else "$baseId:${network.interfaceName}"
        if (id != baseId && !used.add(id)) continue
        val name = if (id != baseId && network.interfaceName.isNotEmpty()) "$baseName (${network.interfaceName})" else baseName
        out += MultilinkHostLink(
            id = id,
            name = name,
            kind = kind,
            ipv4 = ipv4.orEmpty(),
            ipv6 = ipv6?.substringBefore("%").orEmpty(),
            handle = network.handle,
            phone = network.metered,
        )
    }
    return out.sortedBy { it.id }
}

private fun isUsableIpv4(address: String): Boolean {
    val parts = address.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] != 127 && !(octets[0] == 169 && octets[1] == 254)
}

/** A global unicast address: not link-local, not a unique local one (fc00::/7), not loopback, site-local or multicast. */
internal fun isGlobalIpv6(address: String): Boolean {
    val text = address.substringBefore("%")
    if (!text.contains(':') || text.contains('.')) return false
    val groups = text.split(':').filter { it.isNotEmpty() }
    if (groups.any { group -> group.length > 4 || group.any { it.digitToIntOrNull(16) == null } }) return false
    val values = groups.map { it.toInt(16) }
    val unspecified = values.all { it == 0 }
    val loopback = values.isNotEmpty() && values.last() == 1 && values.dropLast(1).all { it == 0 }
    if (unspecified || loopback) return false
    val first = if (text.startsWith(":")) 0 else values.first()
    return (first and 0xFFC0) != 0xFE80 && // link-local fe80::/10
        (first and 0xFFC0) != 0xFEC0 && // site-local fec0::/10
        (first and 0xFE00) != 0xFC00 && // unique local fc00::/7
        (first and 0xFF00) != 0xFF00 // multicast
}
