// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import engine.xray.XrayDnsPlan
import engine.xray.buildXrayDnsConfig
import engine.xray.XraySniffingOptions
import engine.xray.toJson
import engine.xray.XrayFragmentOutboundOptions
import engine.xray.XrayMuxOutboundOptions
import engine.xray.XrayOutboundPlan
import engine.xray.XrayOutboundRenderingOptions
import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.XrayRoutingPlan
import engine.xray.XrayRoutingRequest
import engine.xray.XrayRoutingRuleValidator
import engine.xray.XrayTags
import engine.xray.buildXrayBalancers
import engine.xray.buildXrayObservatory
import engine.xray.buildXrayRouting
import engine.xray.planXrayRouting
import engine.xray.renderXrayOutbounds
import features.routing.model.RouteRule
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.isCompositeProxyServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Minimal Xray process configuration for a selected, single server on desktop. */
data class LocalProxyXrayConfigOptions(
    val socksPort: Int = DefaultLocalSocksPort,
    /** A separate HTTP CONNECT inbound used by Windows system-proxy integration. */
    val httpProxyPort: Int = DefaultLocalHttpProxyPort,
    /**
     * Windows Internet Settings always points to this loopback-only inbound.
     * It deliberately does not inherit [listenAddress], because SOCKS may be
     * shared with a LAN while a system proxy must never be published on it.
     */
    val httpProxyListenAddress: String = DefaultLocalHttpProxyListenAddress,
    val enableHttpProxy: Boolean = false,
    val listenAddress: String = "127.0.0.1",
    /** Blank usernames retain Android's unauthenticated SOCKS settings. */
    val username: String = "",
    val password: String = "",
    val logLevel: String = "warning",
    val dnsServers: List<String> = emptyList(),
    val includeDefaultRouting: Boolean = false,
    /** Global Mux overlay for generated proxy outbounds; null when disabled. */
    val mux: XrayMuxOutboundOptions? = null,
    /** Global Fragment overlay for generated proxy outbounds; null when disabled. */
    val fragment: XrayFragmentOutboundOptions? = null,
    /** Sniffing added to the SOCKS inbound by [LocalProxyXrayConfigFactory.buildFromPlan]; null adds none. */
    val sniffing: XraySniffingOptions? = null,
)

object LocalProxyXrayConfigFactory {
    const val ProxyTag = "skipi-proxy"
    const val SocksInboundTag = "skipi-socks"
    const val HttpInboundTag = "skipi-http"
    const val DirectTag = "direct"
    const val BlockTag = "block"

    /** Builds the SOCKS inbound with the exact fields used by Android's local listener. */
    fun buildSocksInbound(
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
        tag: String = SocksInboundTag,
    ) = buildJsonObject {
        require(options.socksPort in 1..65_535) { "Local SOCKS port must be in 1..65535" }
        require(options.listenAddress.isNotBlank()) { "Local SOCKS listen address must not be blank" }
        put("tag", tag)
        put("listen", options.listenAddress)
        put("port", options.socksPort)
        put("protocol", "socks")
        put(
            "settings",
            buildJsonObject {
                put("auth", if (options.username.isBlank()) "noauth" else "password")
                put("udp", true)
                put("userLevel", 0)
                if (options.listenAddress == "127.0.0.1") put("ip", "127.0.0.1")
                if (options.username.isNotBlank()) {
                    put(
                        "users",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("user", options.username)
                                    put("pass", options.password)
                                },
                            )
                        },
                    )
                }
            },
        )
    }

    /** Builds the same local listener around a planner-produced proxy graph. */
    fun buildFromPlan(
        plan: XrayOutboundPlan,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
        renderingOptions: XrayOutboundRenderingOptions,
        routingPlan: XrayRoutingPlan? = null,
    ): String {
        validateOptions(options)

        val effectiveRoutingPlan = routingPlan ?: buildLocalRoutingPlan(
            plan = plan,
            options = options,
            renderingOptions = renderingOptions,
        )
        val effectiveRenderingOptions = renderingOptions.copy(
            primaryOutboundTag = effectiveRoutingPlan?.primaryOutboundTag ?: renderingOptions.primaryOutboundTag,
            mux = renderingOptions.mux ?: options.mux,
            fragment = renderingOptions.fragment ?: options.fragment,
        )
        val outbounds = renderXrayOutbounds(plan.proxyOutbounds, effectiveRenderingOptions)
        val routing = effectiveRoutingPlan?.let(::buildXrayRouting)
        val observatory = buildXrayObservatory(
            selectors = plan.observatorySelectors,
            probeUrl = plan.observatoryProbeUrl,
            probeInterval = plan.observatoryProbeInterval,
            probeTimeout = plan.observatoryProbeTimeout,
        )

        return buildConfigJson(
            options = options,
            outbounds = outbounds,
            routing = routing,
            observatory = observatory,
            dnsPlan = renderingOptions.dnsPlan,
        )
    }

    fun build(
        server: ProxyServer<*>,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
    ): String {
        validateOptions(options)
        require(server !is Custom) { "Raw custom Xray JSON needs a dedicated desktop adapter" }
        require(!server.isCompositeProxyServer()) { "Proxy groups need a resolved desktop member before starting Xray" }

        val validationIssues = server.validateFull()
        require(validationIssues.isEmpty()) {
            "Selected server is invalid: ${validationIssues.joinToString { it.error.name }}"
        }

        val outbounds = buildJsonArray {
            add(server.toXrayOutbound(ProxyTag).toJsonObject())
            add(buildSimpleLegacyOutbound(DirectTag, "freedom"))
            add(buildSimpleLegacyOutbound(BlockTag, "blackhole"))
        }
        return buildConfigJson(
            options = options,
            outbounds = outbounds,
            routing = if (options.includeDefaultRouting) buildLegacyDefaultRouting() else null,
        )
    }

    fun buildStandaloneConfig(
        server: ProxyServer<*>,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(
            enableHttpProxy = true,
            dnsServers = listOf("1.1.1.1", "8.8.8.8"),
            includeDefaultRouting = true,
        ),
    ): String = build(server, options)

    private fun buildLocalRoutingPlan(
        plan: XrayOutboundPlan,
        options: LocalProxyXrayConfigOptions,
        renderingOptions: XrayOutboundRenderingOptions,
    ): XrayRoutingPlan? {
        val selectedTarget = plan.routeTargets[XrayTags.PROXY]
        val shouldBuildRouting = options.includeDefaultRouting || selectedTarget?.kind == XrayRouteTargetKind.Balancer
        if (!shouldBuildRouting) return null

        val routeTargets = if (options.includeDefaultRouting) {
            plan.routeTargets + (XrayTags.DIRECT to XrayRouteTarget(XrayTags.DIRECT, XrayRouteTargetKind.Outbound))
        } else {
            plan.routeTargets
        }
        val routeRules = if (options.includeDefaultRouting) {
            listOf(RouteRule(outboundTag = XrayTags.DIRECT, ip = listOf(PrivateGeoIpRule)))
        } else {
            emptyList()
        }
        val dnsRouting = renderingOptions.dnsPlan?.routingOptions
        return planXrayRouting(
            request = XrayRoutingRequest(
                routeDomainStrategy = LocalRoutingDomainStrategy,
                routeRules = routeRules,
                defaultRouteOutboundTag = XrayTags.PROXY,
                routeTargets = routeTargets,
                balancers = buildXrayBalancers(plan.balancers),
                enableLocalDns = renderingOptions.enableLocalDns,
                routeProxyDns = dnsRouting?.routeProxyDns == true,
                routeDirectDns = dnsRouting?.routeDirectDns == true,
                dnsHijackInboundTags = emptyList(),
            ),
            ruleValidator = LocalProxyRoutingRuleValidator,
        )
    }

    private fun buildConfigJson(
        options: LocalProxyXrayConfigOptions,
        outbounds: JsonArray,
        routing: JsonObject? = null,
        observatory: JsonObject? = null,
        dnsPlan: XrayDnsPlan? = null,
    ): String {
        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", options.logLevel) })
            if (options.dnsServers.isNotEmpty()) {
                put("dns", buildJsonObject {
                    put("servers", buildJsonArray { options.dnsServers.forEach { add(JsonPrimitive(it)) } })
                    put("queryStrategy", "UseIPv4")
                })
            } else if (dnsPlan != null) {
                put("dns", buildXrayDnsConfig(dnsPlan))
            }
            put("inbounds", buildJsonArray {
                add(buildSocksInbound(options).withSniffing(options.sniffing))
                if (options.enableHttpProxy) {
                    add(buildJsonObject {
                        put("tag", HttpInboundTag)
                        put("listen", options.httpProxyListenAddress)
                        put("port", options.httpProxyPort)
                        put("protocol", "http")
                        put("settings", buildJsonObject { put("allowTransparent", false) })
                    })
                }
            })
            put("outbounds", outbounds)
            routing?.let { put("routing", it) }
            observatory?.let { put("observatory", it) }
        }
        return XrayConfigJson.encodeToString(config) + "\n"
    }

    private fun buildLegacyDefaultRouting(): JsonObject = buildJsonObject {
        put("domainStrategy", "IPIfNonMatch")
        put("rules", buildJsonArray {
            add(buildJsonObject {
                put("type", "field")
                put("ip", buildJsonArray { add(JsonPrimitive(PrivateGeoIpRule)) })
                put("outboundTag", DirectTag)
            })
            add(buildJsonObject {
                put("type", "field")
                put("network", "tcp,udp")
                put("outboundTag", ProxyTag)
            })
        })
    }

    /** Validates local listener options before a host performs its own config planning. */
    fun validateOptions(options: LocalProxyXrayConfigOptions) {
        require(options.socksPort in 1..65_535) { "Local SOCKS port must be in 1..65535" }
        if (options.enableHttpProxy) {
            require(options.httpProxyPort in 1..65_535) { "Local HTTP proxy port must be in 1..65535" }
            require(options.httpProxyPort != options.socksPort) {
                "Local HTTP proxy port must differ from the SOCKS port"
            }
            require(options.httpProxyListenAddress == DefaultLocalHttpProxyListenAddress) {
                "Windows system HTTP proxy must listen on $DefaultLocalHttpProxyListenAddress"
            }
        }
        require(options.listenAddress.isNotBlank()) { "Local SOCKS listen address must not be blank" }
    }

    private fun buildSimpleLegacyOutbound(tag: String, protocol: String) = buildJsonObject {
        put("tag", tag)
        put("protocol", protocol)
    }
}

const val DefaultLocalSocksPort = 10_808
const val DefaultLocalHttpProxyPort = 10_809
const val DefaultLocalHttpProxyListenAddress = "127.0.0.1"

private val XrayConfigJson = Json {
    encodeDefaults = true
}

private const val PrivateGeoIpRule = "geoip:private"
private const val LocalRoutingDomainStrategy = 1

private object LocalProxyRoutingRuleValidator : XrayRoutingRuleValidator {
    override fun isDomainRuleValid(rule: String): Boolean = true
    override fun isIpRuleValid(rule: String): Boolean = true
}

/** Adds Android's inbound sniffing object when the host configured one. */
fun JsonObject.withSniffing(sniffing: XraySniffingOptions?): JsonObject =
    if (sniffing == null) this else buildJsonObject {
        this@withSniffing.forEach { (key, value) -> put(key, value) }
        put("sniffing", sniffing.toJson())
    }
