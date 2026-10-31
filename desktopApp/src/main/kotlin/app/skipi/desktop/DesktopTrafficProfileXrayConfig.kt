// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.buildLogJson
import engine.xray.XrayResourceRoutingRuleValidator
import engine.xray.ProfileProxyTag
import engine.xray.XrayTags
import engine.xray.buildXrayDnsConfig
import engine.xray.startupProxyServerHostDnsDomains
import features.config.TrafficConfigDnsInputs
import features.config.TrafficConfigRuntimePlanInput
import features.config.TrafficConfigRuntimePlanner
import features.proxy.server.model.serverHost
import engine.xray.XraySniffingOptions
import platform.withSniffing
import engine.xray.XrayFragmentOutboundOptions
import engine.xray.XrayMuxOutboundOptions
import engine.xray.buildXrayFragmentOutbound
import engine.xray.withXrayMuxAndFragmentOverlay
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import engine.xray.XrayProtocols
import engine.vpn.VpnDefaults
import engine.vpn.buildLoopbackSocksOutbound
import engine.vpn.planAmneziaWgBridge
import engine.xray.TrafficProfileServerRecord
import features.proxy.server.model.AmneziaWg
import kotlinx.serialization.json.jsonPrimitive
import engine.xray.TrafficProfileXrayCapabilities
import engine.xray.TrafficProfileXrayPlanner
import engine.xray.buildXrayBalancers
import engine.xray.buildFreedomOutbound
import engine.xray.buildXrayObservatory
import engine.xray.buildSimpleOutbound
import engine.xray.toSupportedXrayDnsServers
import engine.xray.toXrayRoutingDomainStrategy
import engine.xray.xrayDirectOutboundDomainStrategy
import features.config.TrafficConfigReference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.DefaultLocalHttpProxyListenAddress
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions

/**
 * Desktop adapter for the portable part of Android's TrafficConfigRuntime.
 *
 * It deliberately starts with the shared Shadowrocket parser and the shared
 * ProxyServer -> Xray outbound mapping. Android-only VPN/TUN, per-app and
 * battery controls do not leak into this desktop runtime.
 */
object DesktopTrafficProfileXrayConfigFactory {
    fun build(
        profile: DesktopStoredConfig,
        serverLibrary: DesktopServerLibrary,
        options: LocalProxyXrayConfigOptions,
        configs: List<DesktopStoredConfig> = emptyList(),
    ): String = buildGenerated(profile, serverLibrary, options, configs).json

    /** Builds the configuration and the AmneziaWG runner it needs started before Core, if any. */
    internal fun buildGenerated(
        profile: DesktopStoredConfig,
        serverLibrary: DesktopServerLibrary,
        options: LocalProxyXrayConfigOptions,
        configs: List<DesktopStoredConfig> = emptyList(),
        amneziaWgDnsServers: List<String> = listOf(VpnDefaults.IPV4_DNS),
    ): DesktopGeneratedXrayConfig {
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

        val decodedServers = serverLibrary.servers.mapNotNull { stored ->
            stored.decode().getOrNull()?.let { server -> TrafficProfileServerRecord(stored.id, server) }
        }
        val plan = TrafficProfileXrayPlanner.plan(
            profileContent = profile.content,
            servers = decodedServers,
            selectedServerId = serverLibrary.selectedServerId,
            capabilities = DesktopTrafficProfileXrayCapabilities,
            configs = configs.map { config -> TrafficConfigReference(config.id, config.content) },
        )
        val analysis = plan.analysis
        // AmneziaWG runs in Core's native runner; Xray reaches it through a loopback SOCKS outbound.
        val amneziaWgPlan = planAmneziaWgBridge(
            servers = plan.proxyServers.filterIsInstance<AmneziaWg>(),
            dnsServers = amneziaWgDnsServers,
            reservedPorts = options.reservedLocalPorts(),
        )
        val proxyOutbounds = if (amneziaWgPlan == null) {
            plan.proxyOutbounds
        } else {
            // proxyServers is filled together with proxyOutbounds, one entry each.
            plan.proxyOutbounds.mapIndexed { index, outbound ->
                val server = plan.proxyServers[index]
                if (server is AmneziaWg && server.matchesRuntimeConfig(amneziaWgPlan.server)) {
                    buildLoopbackSocksOutbound(
                        tag = outbound.getValue("tag").jsonPrimitive.content,
                        port = amneziaWgPlan.socksPort,
                    )
                } else {
                    outbound
                }
            }
        }
        val routingRules = plan.routingRules
        val balancers = plan.balancers

        // Like Android's withActiveTrafficConfigApplied, the active profile replaces global Mux/Fragment.
        val profileSettings = TrafficConfigState(id = profile.id, name = profile.name, rawConfig = profile.content)
            .withSkipiSettingsReadFromRawConfig()
            .androidSettings
        // Same precedence as Android's withActiveTrafficConfigApplied: profile [SKIPI]/[General]/[Host]
        // values replace the global defaults, then the shared DNS planner builds the section.
        val runtimePlan = TrafficConfigRuntimePlanner.plan(
            TrafficConfigRuntimePlanInput(
                rawConfig = profile.content,
                configs = configs.map { config -> TrafficConfigReference(config.id, config.content) },
                servers = emptyList(),
                enableIpv6Fallback = false,
                enableIpv6PreferFallback = false,
                dnsInputs = TrafficConfigDnsInputs(
                    profileProxyDns = profileSettings.proxyDns,
                    profileDirectDns = profileSettings.directDns,
                    profileDirectDnsDomains = profileSettings.directDnsDomains,
                    profileDnsHosts = profileSettings.dnsHosts,
                    existingProxyDns = DesktopDnsInputs().proxyDns,
                    existingDirectDns = DesktopDnsInputs().directDns,
                    existingDirectDnsDomains = DesktopDnsInputs().directDnsDomains,
                    existingDnsHosts = DesktopDnsInputs().dnsHosts,
                ),
            ),
        )
        val dnsPlan = planDesktopXrayDns(
            inputs = DesktopDnsInputs(
                proxyDns = runtimePlan.proxyDns,
                directDns = runtimePlan.directDns,
                directDnsDomains = runtimePlan.directDnsDomains,
                dnsHosts = runtimePlan.dnsHosts,
                enableIpv6 = runtimePlan.enableIpv6,
            ),
            startupProxyServerDomains = plan.proxyServers.mapNotNull { it.serverHost() }.startupProxyServerHostDnsDomains(),
            ruleValidator = XrayResourceRoutingRuleValidator(hasResourceTag = null),
        )
        val ipv6Enabled = runtimePlan.enableIpv6
        val ipv6Preferred = runtimePlan.enableIpv6Prefer
        // Android applies the stored profile setting as is; IPv6 alone does not pick a strategy.
        val routeDomainStrategy = profileSettings.routeDomainStrategy.toXrayRoutingDomainStrategy()

        val muxOptions = if (profileSettings.enableMux) {
            XrayMuxOutboundOptions(
                profileSettings.muxConcurrency,
                profileSettings.muxXudpConcurrency,
                profileSettings.muxXudpProxyUdp443,
            )
        } else {
            null
        }
        val fragmentOptions = if (profileSettings.enableFragment) {
            XrayFragmentOutboundOptions(
                domainStrategy = xrayDirectOutboundDomainStrategy(ipv6Enabled, ipv6Preferred),
                packets = profileSettings.fragmentPackets,
                length = profileSettings.fragmentLength,
                interval = profileSettings.fragmentInterval,
            )
        } else {
            null
        }
        val overlaidProxyOutbounds = proxyOutbounds.withXrayMuxAndFragmentOverlay(muxOptions, fragmentOptions)

        // Upstream DNS queries must bypass the user's rules, as in Android's routing plan.
        val dnsUpstreamRules = buildList {
            if (dnsPlan.routingOptions.routeDirectDns) add(dnsUpstreamRule(XrayTags.DIRECT_DNS, DirectTag))
            if (dnsPlan.routingOptions.routeProxyDns) add(dnsUpstreamRule(XrayTags.PROXY_DNS, ProfileProxyTag))
        }
        val config = buildJsonObject {
            put("log", options.buildLogJson())
            put("dns", buildXrayDnsConfig(dnsPlan))
            put(
                "inbounds",
                buildJsonArray {
                    add(
                        LocalProxyXrayConfigFactory.buildSocksInbound(options, tag = SocksInboundTag).withSniffing(
                            XraySniffingOptions(
                                enabled = profileSettings.enableSniffing,
                                routeOnly = profileSettings.enableSniffingRouteOnly,
                            ),
                        ),
                    )
                    if (options.enableHttpProxy) {
                        add(
                            buildJsonObject {
                                put("tag", LocalProxyXrayConfigFactory.HttpInboundTag)
                                put("listen", options.httpProxyListenAddress)
                                put("port", options.httpProxyPort)
                                put("protocol", "http")
                                put(
                                    "settings",
                                    buildJsonObject {
                                        put("allowTransparent", false)
                                    },
                                )
                            },
                        )
                    }
                },
            )
            put(
                "outbounds",
                buildJsonArray {
                    overlaidProxyOutbounds.forEach(::add)
                    add(
                        buildFreedomOutbound(
                            tag = DirectTag,
                            domainStrategy = xrayDirectOutboundDomainStrategy(
                                enableIpv6 = ipv6Enabled,
                                enableIpv6Prefer = ipv6Preferred,
                            ),
                            tcpSockopt = options.tcpSockopt,
                        ),
                    )
                    add(buildSimpleOutbound(BlockTag, XrayProtocols.BLACKHOLE))
                    fragmentOptions?.let { add(buildXrayFragmentOutbound(it)) }
                },
            )
            put(
                "routing",
                buildJsonObject {
                    put("domainStrategy", routeDomainStrategy)
                    putJsonArray("rules") {
                        dnsUpstreamRules.forEach(::add)
                        routingRules.forEach(::add)
                    }
                    if (balancers.isNotEmpty()) {
                        putJsonArray("balancers") { buildXrayBalancers(balancers).forEach(::add) }
                    }
                },
            )
            buildXrayObservatory(
                selectors = plan.observatorySelectors,
                probeUrl = plan.observatoryUrl,
                probeInterval = plan.observatoryInterval,
                fallbackProbeUrl = DefaultProbeUrl,
                fallbackProbeInterval = DefaultProbeInterval,
            )?.let { observatory -> put("observatory", observatory) }
        }
        return DesktopGeneratedXrayConfig(
            json = DesktopProfileXrayJson.encodeToString(config) + "\n",
            amneziaWg = amneziaWgPlan?.toDesktopBridge(),
        )
    }
}

private fun List<String>.toDesktopXrayHosts(): Map<String, List<String>> = buildMap {
    this@toDesktopXrayHosts.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith(';')) return@forEach
        val separator = trimmed.indexOf('=')
        if (separator <= 0 || separator == trimmed.lastIndex) return@forEach
        val domain = trimmed.substring(0, separator).trim()
        val targets = trimmed.substring(separator + 1)
            .split(',')
            .map { target -> target.trim().removePrefix("server:") }
            .filter(String::isNotEmpty)
            .distinct()
        if (domain.isNotEmpty() && targets.isNotEmpty()) put(domain, targets)
    }
}

private fun String?.toConfigBooleanOrDefault(defaultValue: Boolean): Boolean = when (this?.trim()?.lowercase()) {
    "true", "yes", "1" -> true
    "false", "no", "0" -> false
    else -> defaultValue
}

private const val SocksInboundTag = "skipi-socks"
private const val DirectTag = "direct"
private const val BlockTag = "block"
private const val DefaultProbeUrl = "https://www.gstatic.com/generate_204"
private const val DefaultProbeInterval = "10s"

private val DesktopTrafficProfileXrayCapabilities = TrafficProfileXrayCapabilities(
    supportsIpAsnRules = false,
    supportedExternalRuleSetFiles = setOf("geosite.dat", "geoip.dat"),
    unsupportedIpAsnMessage = { value ->
        "IP-ASN rule '$value' is not supported by the bundled Desktop Xray resources"
    },
    externalRuleSetReferenceMessage = { value ->
        "RULE-SET '$value' needs an external rule-set file, which Desktop SKIPI does not bundle"
    },
    unsupportedExternalRuleSetMessage = { source, data ->
        "External rule-set '$source' cannot be used as $data data on Desktop SKIPI"
    },
)

private val DesktopProfileXrayJson = Json { encodeDefaults = true }

private fun dnsUpstreamRule(inboundTag: String, outboundTag: String) = buildJsonObject {
    putJsonArray("inboundTag") { add(inboundTag) }
    put("outboundTag", outboundTag)
}
