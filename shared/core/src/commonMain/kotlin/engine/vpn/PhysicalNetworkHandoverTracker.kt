// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

/**
 * Keeps handover detection independent from Android callbacks so duplicate
 * capability events never restart the VPN runtime more than once.
 *
 * A handover is a different network, or the same network moving to another
 * link (interface or address), as a cellular cell change does: Android keeps
 * the same network object while the sockets of the old path stall.
 */
class PhysicalNetworkHandoverTracker<T> {
    private var initialized = false
    private var currentNetwork: T? = null
    private var currentLink: String? = null
    private var recoveryPending = false

    fun reset(network: T?, link: String? = null) {
        initialized = true
        currentNetwork = network
        currentLink = link
        recoveryPending = false
    }

    fun clear() {
        initialized = false
        currentNetwork = null
        currentLink = null
        recoveryPending = false
    }

    /**
     * Returns true only after a usable network replaces the previous one, or the
     * current one moves to another [link]. A loss itself does not restart the
     * runtime; recovery waits for an upstream. An unknown link ([link] or the
     * previous one null) is adopted without a reload.
     */
    fun observe(network: T?, link: String? = null): Boolean {
        if (!initialized) {
            reset(network, link)
            return false
        }
        if (currentNetwork == network) {
            val moved = network != null && link != null && currentLink != null && link != currentLink
            if (link != null) currentLink = link
            if (moved) recoveryPending = true
            return moved
        }

        val previousNetwork = currentNetwork
        currentNetwork = network
        currentLink = link
        recoveryPending = network != null && previousNetwork != network
        return recoveryPending
    }

    /**
     * A deferred external VPN restart must not discard a detected handover.
     * The service acknowledges it only after its own recovery has started.
     */
    fun hasPendingRecovery(network: T?): Boolean {
        return network != null && currentNetwork == network && recoveryPending
    }

    fun acknowledgeRecovery(network: T?) {
        if (network != null && currentNetwork == network) {
            recoveryPending = false
        }
    }
}

/**
 * Identifies the path of a physical link: its interface plus its IPv4 addresses and IPv6 /64 prefixes.
 * IPv6 interface identifiers are ignored because privacy extensions rotate them without a path change,
 * and link-local addresses never carry traffic off the link.
 */
fun physicalLinkFingerprint(interfaceName: String?, addresses: List<ByteArray>): String {
    val parts = addresses.mapNotNull { address ->
        when (address.size) {
            4 -> if (address[0].toInt() and 0xff == 169 && address[1].toInt() and 0xff == 254) {
                null
            } else {
                address.toHex(4)
            }
            16 -> if (address[0].toInt() and 0xff == 0xfe && address[1].toInt() and 0xc0 == 0x80) {
                null
            } else {
                address.toHex(8)
            }
            else -> null
        }
    }.distinct().sorted()
    return interfaceName.orEmpty() + "|" + parts.joinToString(",")
}

private fun ByteArray.toHex(length: Int): String = buildString {
    for (index in 0 until length) {
        append(HexDigits[(this@toHex[index].toInt() shr 4) and 0xf])
        append(HexDigits[this@toHex[index].toInt() and 0xf])
    }
}

private const val HexDigits = "0123456789abcdef"
