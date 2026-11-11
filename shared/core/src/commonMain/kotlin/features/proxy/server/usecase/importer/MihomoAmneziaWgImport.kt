// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg

/** Imports an AmneziaWG proxy from a Mihomo/Clash YAML object. */
fun MihomoYamlMap.toMihomoAmneziaWgProxyServer(): AmneziaWg {
    val peers = list("peers").orEmpty().mapNotNull { item -> item.asStringMap() }
    if (peers.size > 1) {
        unsupported("multiple AmneziaWG peers are not supported")
    }
    val peer = peers.firstOrNull()
    val endpoint = peer ?: this
    val awgOptions = this["amnezia-wg-option"]?.asStringMap() ?: this

    return AmneziaWg(
        remarks = requiredString("name"),
        server = endpoint.requiredString("server"),
        port = endpoint.requiredString("port"),
        secretKey = requiredString("private-key", "privateKey"),
        publicKey = endpoint.requiredString("public-key", "publicKey"),
        preSharedKey = endpoint.string("pre-shared-key", "presharedkey", "preSharedKey").orEmpty(),
        reserved = endpoint.wireguardReservedString("AmneziaWG"),
        address = wireguardAddressString("AmneziaWG"),
        mtu = string("mtu") ?: "1420",
        jc = awgOptions.string("jc") ?: "4",
        jmin = awgOptions.string("jmin") ?: "40",
        jmax = awgOptions.string("jmax") ?: "70",
        s1 = awgOptions.string("s1") ?: "15",
        s2 = awgOptions.string("s2") ?: "30",
        s3 = awgOptions.string("s3") ?: "",
        s4 = awgOptions.string("s4") ?: "",
        h1 = awgOptions.string("h1") ?: "",
        h2 = awgOptions.string("h2") ?: "",
        h3 = awgOptions.string("h3") ?: "",
        h4 = awgOptions.string("h4") ?: "",
        i1 = awgOptions.string("i1") ?: "",
        i2 = awgOptions.string("i2") ?: "",
        i3 = awgOptions.string("i3") ?: "",
        i4 = awgOptions.string("i4") ?: "",
        i5 = awgOptions.string("i5") ?: "",
    )
}
