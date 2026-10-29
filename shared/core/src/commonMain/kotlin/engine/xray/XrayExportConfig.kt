// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.DefaultLocalHttpProxyPort
import platform.DefaultLocalSocksPort

/** Host inputs of the standalone export; the outbound plan comes from the shared planner. */
data class XrayExportInput(
    val outboundPlan: XrayOutboundPlan,
    /** Raw local proxy port text as stored by the host; an invalid value falls back to the default. */
    val localProxyPort: String,
    val proxyDnsServers: List<String>,
    val enableIpv6: Boolean,
    val mux: XrayMuxOutboundOptions? = null,
    val fragment: XrayFragmentOutboundOptions? = null,
    val transport: XrayOutboundTransportOptions? = null,
    /** Hosts with a VPN local DNS also export its `dns-out` outbound. */
    val enableLocalDns: Boolean = false,
)

/**
 * The self-contained Xray configuration behind "Copy full JSON": local SOCKS/HTTP inbounds,
 * private addresses direct, everything else through the selected server or its balancer. It is
 * the same document on every host and is independent of the active profile and runtime state.
 */
object XrayExportConfig {
    fun generated(input: XrayExportInput): String {
        val plan = input.outboundPlan
        val primaryOutboundTag = plan.proxyOutbounds.firstOrNull()?.tag ?: XrayTags.PROXY
        val outbounds = renderXrayOutbounds(
            proxyOutbounds = plan.proxyOutbounds,
            options = XrayOutboundRenderingOptions(
                primaryOutboundTag = primaryOutboundTag,
                enableLocalDns = input.enableLocalDns,
                dnsPlan = null,
                dnsProxyOutboundTag = null,
                transport = input.transport,
                mux = input.mux,
                fragment = input.fragment,
            ),
        )

        val dnsServers = (
            input.proxyDnsServers.filter { it.isNotBlank() && !it.startsWith("172.") } +
                listOf("1.1.1.1", "8.8.8.8")
            ).distinct()
        val dns = buildJsonObject {
            putJsonArray("servers") { dnsServers.forEach(::add) }
            put("queryStrategy", if (input.enableIpv6) "UseIP" else "UseIPv4")
        }

        val balancer = plan.balancers.firstOrNull()
        val routing = buildJsonObject {
            put("domainStrategy", "IPIfNonMatch")
            putJsonArray("rules") {
                add(
                    buildJsonObject {
                        put("type", "field")
                        putJsonArray("ip") { add("geoip:private") }
                        put("outboundTag", XrayTags.DIRECT)
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "field")
                        put("network", "tcp,udp")
                        if (balancer != null) put("balancerTag", balancer.tag) else put("outboundTag", primaryOutboundTag)
                    },
                )
            }
            if (plan.balancers.isNotEmpty()) {
                put("balancers", buildXrayBalancers(plan.balancers).toJsonObjectArray())
            }
        }
        val observatory = buildXrayObservatory(
            selectors = plan.observatorySelectors,
            probeUrl = plan.observatoryProbeUrl,
            probeInterval = plan.observatoryProbeInterval,
            probeTimeout = plan.observatoryProbeTimeout,
        )

        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("dns", dns)
            put("inbounds", exportInbounds(input.localProxyPort))
            put("outbounds", outbounds)
            put("routing", routing)
            observatory?.let { put("observatory", it) }
        }
        return XrayConfigJson.encodeToString(config) + "\n"
    }

    /** A raw Custom server is exported as written, with the log and (optionally) inbounds replaced. */
    fun custom(configJson: String, overrideInboundAndDns: Boolean, localProxyPort: String): String {
        val customJson = parseCustomXrayConfigJsonObject(configJson)
        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            val ownInbounds = customJson["inbounds"]
            if (ownInbounds != null && !overrideInboundAndDns) {
                put("inbounds", ownInbounds)
            } else {
                put("inbounds", exportInbounds(localProxyPort))
            }
            customJson.forEach { (key, value) ->
                if (key != "inbounds" && key != "log") put(key, value)
            }
        }
        return formatCustomXrayConfigJson(config)
    }

    private fun exportInbounds(localProxyPort: String): JsonArray {
        val socksPort = localProxyPort.toIntOrNull()?.takeIf { it in 1..65_535 } ?: DefaultLocalSocksPort
        val httpPort = if (socksPort != DefaultLocalHttpProxyPort) DefaultLocalHttpProxyPort else 10_810
        return buildJsonArray {
            add(
                buildJsonObject {
                    put("tag", "socks")
                    put("listen", "127.0.0.1")
                    put("port", socksPort)
                    put("protocol", "socks")
                    put("settings", buildJsonObject {
                        put("auth", "noauth")
                        put("udp", true)
                    })
                },
            )
            add(
                buildJsonObject {
                    put("tag", "http")
                    put("listen", "127.0.0.1")
                    put("port", httpPort)
                    put("protocol", "http")
                    put("settings", buildJsonObject { put("allowTransparent", false) })
                },
            )
        }
    }
}
