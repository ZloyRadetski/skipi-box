// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_select_server_before_connect
import platform.buildLogJson
import engine.xray.CustomXrayDnsRewriteRequest
import engine.xray.XrayResourceRoutingRuleValidator
import engine.xray.XraySniffingOptions
import engine.xray.XrayTags
import engine.xray.CustomSkipiRouting
import engine.xray.withStrictFullTunnelRouting
import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.XrayRoutingRequest
import engine.xray.planXrayRouting
import engine.xray.toXrayRoutingDomainStrategy
import app.skipi.app.proxy.ProxyServerRow
import app.skipi.app.proxy.XrayOutboundPlanningInput
import app.skipi.app.proxy.rawServerSkipiRouting
import engine.xray.CustomSkipiExtras
import features.proxy.server.model.stripLeadingCountryFlag
import engine.xray.TrafficProfileServerRecord
import engine.xray.TrafficProfileXrayPlan
import engine.xray.TrafficProfileXrayPlanner
import engine.xray.XrayOutboundTransportOptions
import engine.xray.buildXrayBalancers
import engine.xray.buildXrayObservatory
import engine.xray.withXrayProxyOutboundTransport
import engine.xray.xrayWireguardDomainStrategy
import features.config.TrafficConfigReference
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.serverHost
import engine.xray.buildFreedomOutbound
import engine.xray.rewriteCustomXrayDnsConfig
import engine.xray.xrayDirectOutboundDomainStrategy
import engine.network.isIpv4Address
import engine.xray.startupProxyServerHostDnsDomains
import features.proxy.server.model.customXrayConfigProxyServerHosts
import platform.withSniffing
import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.LocalProxyAuth
import platform.LocalProxyJsonAuth
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import engine.vpn.AmneziaWgBridgePlan
import engine.vpn.VpnDefaults
import java.nio.file.Path

/** A generated Core configuration and the AmneziaWG runner it needs started first, if any. */
internal data class DesktopGeneratedXrayConfig(
    val json: String,
    val amneziaWg: DesktopAmneziaWgBridge? = null,
)

/** Desktop entry point shared by preview, runtime start plans, and FullJson export. */
internal fun buildDesktopSelectedServerConfig(
    serverLibrary: DesktopServerLibrary,
    configLibrary: DesktopConfigLibrary,
    routing: RoutingConfigRecord,
    options: LocalProxyXrayConfigOptions,
    trafficConfigContentById: Map<Int, String>,
    subscriptionPingUrl: String,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    routingDataDirectory: Path? = null,
    /** Applies the strict full tunnel at start without rewriting stored routing; Custom configs cannot take it. */
    strictFullTunnel: Boolean = false,
    /** See [buildDesktopSelectedServerConfigWithBridge]. */
    answerTunDns: Boolean = false,
): Result<String>? = buildDesktopSelectedServerConfigWithBridge(
    serverLibrary = serverLibrary,
    configLibrary = configLibrary,
    routing = routing,
    options = options,
    trafficConfigContentById = trafficConfigContentById,
    subscriptionPingUrl = subscriptionPingUrl,
    latencyByServerId = latencyByServerId,
    routingDataDirectory = routingDataDirectory,
    strictFullTunnel = strictFullTunnel,
    answerTunDns = answerTunDns,
)?.map { generated -> generated.json }

/** Like [buildDesktopSelectedServerConfig], keeping the AmneziaWG runner the configuration depends on. */
internal fun buildDesktopSelectedServerConfigWithBridge(
    serverLibrary: DesktopServerLibrary,
    configLibrary: DesktopConfigLibrary,
    routing: RoutingConfigRecord,
    options: LocalProxyXrayConfigOptions,
    trafficConfigContentById: Map<Int, String>,
    subscriptionPingUrl: String,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    routingDataDirectory: Path? = null,
    strictFullTunnel: Boolean = false,
    /** The global TUN DNS; an active profile's own value replaces it, as on Android. */
    tunVpnDns: String = VpnDefaults.IPV4_DNS,
    /**
     * TUN mode: DNS queries reaching the SOCKS inbound on port 53 are answered by Core's DNS, as
     * Android answers its TUN's. The system's resolver sits behind the tunnel, so this keeps names
     * resolved through the VPN and Core's own lookups off the tunnel they would need.
     */
    answerTunDns: Boolean = false,
): Result<DesktopGeneratedXrayConfig>? {
    val selectedStored = serverLibrary.selectedServerId
        ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
    val selectedServerResult = selectedStored?.decode()
    val selectedServer = selectedServerResult?.getOrNull()
    val profileRuntime = configLibrary.activeProfileRuntime()
    val effectiveTunDns = profileRuntime.tunVpnDnsOr(tunVpnDns)
    if (selectedServer is Custom) {
        val dnsInputs = profileRuntime?.toDnsInputs() ?: DesktopDnsInputs(tunDns = effectiveTunDns)
        return checkNotNull(selectedServerResult).mapCatching {
            DesktopGeneratedXrayConfig(
                buildDesktopCustomXrayConfig(
                    server = selectedServer,
                    options = options,
                    dnsInputs = dnsInputs,
                    sniffing = profileRuntime?.sniffingOptions(),
                    strictFullTunnel = strictFullTunnel,
                    answerTunDns = answerTunDns,
                    skipiRouting = customServerSkipiRouting(
                        profile = configLibrary.activeProfile,
                        profileDomainStrategy = profileRuntime?.settings?.routeDomainStrategy,
                        routing = routing,
                        profileServers = {
                            serverLibrary.servers.mapNotNull { stored ->
                                stored.decode().getOrNull()?.let { server -> TrafficProfileServerRecord(stored.id, server) }
                            }
                        },
                        profileConfigs = configLibrary.configs.map { config -> TrafficConfigReference(config.id, config.content) },
                        rawRoutePlanning = DesktopRawRoutePlanning(serverLibrary, trafficConfigContentById, subscriptionPingUrl, latencyByServerId),
                        transport = options.tcpSockopt?.let { sockopt ->
                            XrayOutboundTransportOptions(
                                directDomainStrategy = xrayDirectOutboundDomainStrategy(dnsInputs.enableIpv6, dnsInputs.enableIpv6Prefer),
                                wireguardDomainStrategy = xrayWireguardDomainStrategy(dnsInputs.enableIpv6, dnsInputs.enableIpv6Prefer),
                                tcpSockopt = sockopt,
                            )
                        },
                    ),
                ),
            )
        }
    }

    val activeProfile = configLibrary.activeProfile
    if (activeProfile != null) {
        return runCatching {
            DesktopTrafficProfileXrayConfigFactory.buildGenerated(
                profile = activeProfile,
                serverLibrary = serverLibrary,
                options = options,
                configs = configLibrary.configs,
                amneziaWgDnsServers = amneziaWgDnsServersFor(effectiveTunDns),
                strictFullTunnel = strictFullTunnel,
                answerTunDns = answerTunDns,
            )
        }
    }

    if (selectedStored == null) return null
    return checkNotNull(selectedServerResult).mapCatching {
        DesktopSelectedServerXrayConfigFactory.buildGenerated(
            serverLibrary = serverLibrary,
            routing = if (strictFullTunnel) routing.withStrictFullTunnelApplied() else routing,
            trafficConfigContentById = trafficConfigContentById,
            options = options,
            subscriptionPingUrl = subscriptionPingUrl,
            latencyByServerId = latencyByServerId,
            routingDataDirectory = routingDataDirectory,
            amneziaWgDnsServers = amneziaWgDnsServersFor(effectiveTunDns),
            tunVpnDns = effectiveTunDns,
            answerTunDns = answerTunDns,
        )?.getOrThrow()
            ?: error(desktopText(Res.string.desktop_select_server_before_connect))
    }
}

/**
 * Whether the Core config of the selected server answers the TUN's DNS when asked to: every config
 * SKIPI generates does, a raw JSON only when SKIPI overrides its inbound and DNS. Without it the
 * TUN takes no DNS and the system keeps resolving as before.
 */
internal fun DesktopServerLibrary.coreAnswersTunDns(): Boolean {
    val selected = selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }?.decode()?.getOrNull()
    return (selected as? Custom)?.overrideInboundAndDns ?: true
}

/** Raw resolvers of the AmneziaWG runner's own netstack, as Android passes the TUN DNS. */
internal fun amneziaWgDnsServersFor(tunVpnDns: String): List<String> =
    listOf(tunVpnDns.trim().takeIf(::isIpv4Address) ?: VpnDefaults.IPV4_DNS)

/** Ports the runner's SOCKS bridge must not take: the listeners Core opens for the application. */
internal fun LocalProxyXrayConfigOptions.reservedLocalPorts(): Set<Int> = buildSet {
    add(socksPort)
    if (enableHttpProxy) add(httpProxyPort)
}

internal fun AmneziaWgBridgePlan.toDesktopBridge(): DesktopAmneziaWgBridge =
    DesktopAmneziaWgBridge(configJson = configJson, socksPort = socksPort)

/**
 * Raw Custom entries: the local listener always belongs to Desktop, logging to the application, and
 * with "override inbound and DNS" the application's DNS plan replaces the profile's, as on Android.
 */
internal fun buildDesktopCustomXrayConfig(
    server: Custom,
    options: LocalProxyXrayConfigOptions,
    /** The DNS and IPv6 values of the start: the active profile's, or the application defaults. */
    dnsInputs: DesktopDnsInputs = DesktopDnsInputs(),
    /** The active profile's sniffing; the listener options' own apply without a profile. */
    sniffing: XraySniffingOptions? = null,
    /** False for a latency probe, which must not listen: its ports would collide with a running tunnel's. */
    listeners: Boolean = true,
    log: JsonObject = options.buildLogJson(),
    /** The rules SKIPI applies when the server overrides inbound and DNS; null keeps the JSON's own routing. */
    skipiRouting: CustomSkipiRouting? = null,
    /** The strict full tunnel: no rule of the JSON may leave directly (see [withStrictFullTunnelRouting]). */
    strictFullTunnel: Boolean = false,
    /** TUN mode; applies only with "override inbound and DNS", see [DesktopServerLibrary.coreAnswersTunDns]. */
    answerTunDns: Boolean = false,
): String {
    val customJson = parseCustomXrayConfigJsonObject(server.configJson)
    val inbounds = if (!listeners) emptyList() else buildList {
        add(
            buildJsonObject {
                LocalProxyXrayConfigFactory.buildSocksInbound(options)
                    .withSniffing(sniffing ?: options.sniffing ?: XraySniffingOptions())
                    .forEach { (key, value) ->
                        put(key, value)
                    }
            },
        )
        if (options.enableHttpProxy) add(LocalProxyXrayConfigFactory.buildHttpInbound(options))
    }
    val config = if (server.overrideInboundAndDns) {
        val dnsPlan = planDesktopXrayDns(
            inputs = dnsInputs,
            startupProxyServerDomains = (
                customXrayConfigProxyServerHosts(server.configJson) +
                    skipiRouting?.extras?.invoke(XrayTags.PROXY)?.serverHosts.orEmpty()
                ).startupProxyServerHostDnsDomains(),
            ruleValidator = XrayResourceRoutingRuleValidator(hasResourceTag = null),
        )
        rewriteCustomXrayDnsConfig(
            CustomXrayDnsRewriteRequest(
                config = customJson,
                inbounds = inbounds,
                dnsPlan = dnsPlan,
                enableLocalDns = answerTunDns,
                dnsHijackInboundTags = desktopTunDnsHijackInboundTags(answerTunDns),
                directOutbound = buildFreedomOutbound(
                    tag = XrayTags.DIRECT,
                    domainStrategy = xrayDirectOutboundDomainStrategy(dnsInputs.enableIpv6, dnsInputs.enableIpv6Prefer),
                    tcpSockopt = options.tcpSockopt,
                ),
                skipiRouting = skipiRouting,
            ),
        )
    } else {
        customJson
    }.let { routed -> if (strictFullTunnel) routed.withStrictFullTunnelRouting() else routed }
    val cleanConfig = buildJsonObject {
        put("log", log)
        put("inbounds", JsonArray(inbounds))
        config.forEach { (key, value) ->
            if (key != "inbounds" && key != "log") {
                put(key, value)
            }
        }
    }
    return formatCustomXrayConfigJson(cleanConfig)
}

/**
 * The login the selected raw JSON server carries on its own socks and http inbounds, which the "auto"
 * and "from JSON" modes read. Any other server carries none.
 */
internal fun DesktopServerLibrary.selectedServerJsonAuth(): LocalProxyJsonAuth? {
    val selected = selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }?.decode()?.getOrNull()
    return (selected as? Custom)?.let { custom -> LocalProxyAuth.readJsonAuth(custom.configJson) }
}

/** The server list and settings the global routing rules are planned from when the selected server is a raw JSON. */
internal class DesktopRawRoutePlanning(
    private val serverLibrary: DesktopServerLibrary,
    private val trafficConfigContentById: Map<Int, String>,
    private val subscriptionPingUrl: String,
    private val latencyByServerId: Map<Int, DesktopServerLatencyResult>,
) {
    fun input(routing: RoutingConfigRecord): XrayOutboundPlanningInput {
        val rows = serverLibrary.servers.mapNotNull { stored ->
            stored.decode().getOrNull()?.let { server ->
                ProxyServerRow(
                    id = stored.id,
                    groupId = stored.subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    server = server,
                    latency = latencyByServerId[stored.id].toPlannerLatency(),
                )
            }
        }
        return XrayOutboundPlanningInput(
            selectedServer = rows.first { row -> row.id == serverLibrary.selectedServerId },
            servers = rows,
            routeRules = routing.rules,
            defaultRouteOutboundTag = routing.defaultOutboundTag,
            shadowrocketPolicyGroups = emptyList(),
            trafficConfigContentById = trafficConfigContentById,
            enableLocalDns = false,
            enableFragment = false,
            subscriptionPingUrl = subscriptionPingUrl,
            stripLeadingFlag = { value -> value.stripLeadingCountryFlag() },
        )
    }
}

/**
 * The routing SKIPI puts in a raw server that overrides inbound and DNS: the active profile's rules, or the
 * global routing rules when there is no profile. With neither, the JSON keeps the routing it came with.
 *
 * A profile may send traffic to other servers and to proxy groups. They are added to the raw config next to its own
 * proxy outbound ([profileServers] are the servers they are taken from), so a balancer is a balancer here too; what
 * names the raw server itself, or nothing known, goes to its own outbound.
 */
internal fun customServerSkipiRouting(
    profile: DesktopStoredConfig?,
    profileDomainStrategy: Int?,
    routing: RoutingConfigRecord,
    profileServers: () -> List<TrafficProfileServerRecord> = { emptyList() },
    profileConfigs: List<TrafficConfigReference> = emptyList(),
    transport: XrayOutboundTransportOptions? = null,
    /** What the global rules need to name other servers and groups; without it they only reach the raw server. */
    rawRoutePlanning: DesktopRawRoutePlanning? = null,
): CustomSkipiRouting? = when {
    profile != null -> {
        // The rules and what they need come from one plan; it is made once per proxy tag.
        val plans = HashMap<String, TrafficProfileXrayPlan>()
        fun planFor(proxyTag: String) = plans.getOrPut(proxyTag) {
            TrafficProfileXrayPlanner.plan(
                profileContent = profile.content,
                servers = profileServers().filterNot { record -> record.server is AmneziaWg },
                selectedServerId = null,
                capabilities = DesktopTrafficProfileXrayCapabilities,
                configs = profileConfigs,
                rawProxyTag = proxyTag,
            )
        }
        CustomSkipiRouting(
            domainStrategy = profileDomainStrategy?.toXrayRoutingDomainStrategy(),
            rules = { proxyTag -> planFor(proxyTag).routingRules },
            extras = { proxyTag ->
                val plan = planFor(proxyTag)
                CustomSkipiExtras(
                    outbounds = plan.proxyOutbounds.map { outbound ->
                        transport?.let(outbound::withXrayProxyOutboundTransport) ?: outbound
                    },
                    balancers = buildXrayBalancers(plan.balancers),
                    observatory = buildXrayObservatory(
                        selectors = plan.observatorySelectors,
                        probeUrl = plan.observatoryUrl,
                        probeInterval = plan.observatoryInterval,
                    ),
                    serverHosts = plan.proxyServers.map { server -> server.serverHost() },
                )
            },
        )
    }

    routing.rules.any { rule -> rule.enabled } && rawRoutePlanning != null -> rawServerSkipiRouting(
        input = rawRoutePlanning.input(routing),
        routeDomainStrategy = routing.domainStrategy,
        transport = transport,
        ruleValidator = XrayResourceRoutingRuleValidator(hasResourceTag = null),
    )

    else -> null
}
