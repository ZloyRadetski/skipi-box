// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class PersistedProxyServer(
    val protocol: String,
    val payload: JsonElement,
)

fun ProxyServer<*>.encodePersistedProxyServer(): String {
    return ProxyServer.json.encodeToString(toPersistedProxyServer())
}

fun ProxyServer<*>.toPersistedProxyServer(): PersistedProxyServer {
    return when (this) {
        is HTTP -> persisted(ProxyServerConstants.PROTOCOL_HTTP, this)
        is Socks -> persisted(ProxyServerConstants.PROTOCOL_SOCKS, this)
        is Shadowsocks -> persisted(ProxyServerConstants.PROTOCOL_SS, this)
        is VMess -> persisted(ProxyServerConstants.PROTOCOL_VMESS, this)
        is VLESS -> persisted(ProxyServerConstants.PROTOCOL_VLESS, this)
        is Trojan -> persisted(ProxyServerConstants.PROTOCOL_TROJAN, this)
        is Hysteria2 -> persisted(ProxyServerConstants.PROTOCOL_HYSTERIA2, this)
        is Wireguard -> persisted(ProxyServerConstants.PROTOCOL_WIREGUARD, this)
        is StrategyGroup -> persisted(ProxyServerConstants.PROTOCOL_STRATEGY_GROUP, this)
        is ChainProxy -> persisted(ProxyServerConstants.PROTOCOL_CHAIN_PROXY, this)
        is Custom -> persisted(ProxyServerConstants.PROTOCOL_CUSTOM, this)
        else -> error("Unsupported proxy server type")
    }
}

fun String.decodePersistedProxyServer(): ProxyServer<*> {
    val persistedServer = ProxyServer.json.decodeFromString<PersistedProxyServer>(this)
    return persistedServer.decodeProxyServer()
}

fun PersistedProxyServer.decodeProxyServer(): ProxyServer<*> {
    return when (protocol) {
        ProxyServerConstants.PROTOCOL_HTTP ->
            ProxyServer.json.decodeFromJsonElement<HTTP>(payload)

        ProxyServerConstants.PROTOCOL_SOCKS ->
            ProxyServer.json.decodeFromJsonElement<Socks>(payload)

        ProxyServerConstants.PROTOCOL_SS ->
            ProxyServer.json.decodeFromJsonElement<Shadowsocks>(payload)

        ProxyServerConstants.PROTOCOL_VMESS ->
            ProxyServer.json.decodeFromJsonElement<VMess>(payload)

        ProxyServerConstants.PROTOCOL_VLESS ->
            ProxyServer.json.decodeFromJsonElement<VLESS>(payload)

        ProxyServerConstants.PROTOCOL_TROJAN ->
            ProxyServer.json.decodeFromJsonElement<Trojan>(payload)

        ProxyServerConstants.PROTOCOL_HYSTERIA2 ->
            ProxyServer.json.decodeFromJsonElement<Hysteria2>(payload)

        ProxyServerConstants.PROTOCOL_WIREGUARD ->
            ProxyServer.json.decodeFromJsonElement<Wireguard>(payload)

        ProxyServerConstants.PROTOCOL_STRATEGY_GROUP ->
            ProxyServer.json.decodeFromJsonElement<StrategyGroup>(payload)

        ProxyServerConstants.PROTOCOL_CHAIN_PROXY ->
            ProxyServer.json.decodeFromJsonElement<ChainProxy>(payload)

        ProxyServerConstants.PROTOCOL_CUSTOM ->
            ProxyServer.json.decodeFromJsonElement<Custom>(payload)

        else -> error("Unsupported persisted proxy server protocol: $protocol")
    }
}

private inline fun <reified T> persisted(protocol: String, server: T): PersistedProxyServer {
    return PersistedProxyServer(
        protocol = protocol,
        payload = ProxyServer.json.encodeToJsonElement(server),
    )
}
