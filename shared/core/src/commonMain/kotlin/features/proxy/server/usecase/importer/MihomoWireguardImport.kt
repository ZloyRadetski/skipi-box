// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.Wireguard

fun MihomoYamlMap.toMihomoWireguardProxyServer(): Wireguard {
    val peers = list("peers").orEmpty().mapNotNull { item -> item.asStringMap() }
    if (peers.size > 1) {
        unsupported("multiple WireGuard peers are not supported")
    }
    val peer = peers.firstOrNull()
    val endpoint = peer ?: this
    return Wireguard(
        remarks = requiredString("name"),
        server = endpoint.requiredString("server"),
        port = endpoint.requiredString("port"),
        secretKey = requiredString("private-key", "privateKey"),
        publicKey = endpoint.requiredString("public-key", "publicKey"),
        preSharedKey = endpoint.string("pre-shared-key", "presharedkey", "preSharedKey").orEmpty(),
        reserved = endpoint.wireguardReservedString("WireGuard"),
        address = wireguardAddressString("WireGuard"),
        mtu = string("mtu") ?: "1420",
    )
}
