// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.xray.XrayProtocols
import engine.xray.TrafficProfileServerRecord
import engine.xray.TrafficProfileXrayCapabilities
import engine.xray.TrafficProfileXrayPlanner
import engine.xray.buildXrayBalancers
import engine.xray.buildFreedomOutbound
import engine.xray.buildXrayObservatory
import engine.xray.buildSimpleOutbound
import engine.xray.toSupportedXrayDnsServers
import engine.xray.xrayDirectOutboundDomainStrategy
import features.config.shadowrocketSectionValue
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
    ): String {
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
        )
        val analysis = plan.analysis
        val proxyOutbounds = plan.proxyOutbounds
        val routingRules = plan.routingRules
        val balancers = plan.balancers

        val dnsServers = analysis.general["dns-server"]
            ?.split(',')
            .orEmpty()
            .filterNot { value -> value.trim().equals("system", ignoreCase = true) }
            .toSupportedXrayDnsServers()
        val hosts = analysis.sections["host"].orEmpty().toDesktopXrayHosts()
        val ipv6Enabled = analysis.general["ipv6"].toConfigBooleanOrDefault(false)
        val ipv6Preferred = analysis.general["prefer-ipv6"].toConfigBooleanOrDefault(false)
        val routeDomainStrategy = profile.content
            .shadowrocketSectionValue("SKIPI", "route-domain-strategy")
            .toDesktopRouteDomainStrategyOrNull()
            ?: if (ipv6Enabled) "IPIfNonMatch" else "AsIs"

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("loglevel", options.logLevel)
                },
            )
            if (dnsServers.isNotEmpty() || hosts.isNotEmpty()) {
                put(
                    "dns",
                    buildJsonObject {
                        put("queryStrategy", if (ipv6Enabled) "UseIP" else "UseIPv4")
                        if (dnsServers.isNotEmpty()) {
                            putJsonArray("servers") { dnsServers.forEach(::add) }
                        }
                        if (hosts.isNotEmpty()) {
                            put(
                                "hosts",
                                buildJsonObject {
                                    hosts.forEach { (domain, targets) ->
                                        if (targets.size == 1) {
                                            put(domain, targets.single())
                                        } else {
                                            putJsonArray(domain) { targets.forEach(::add) }
                                        }
                                    }
                                },
                            )
                        }
                    },
                )
            }
            put(
                "inbounds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("tag", SocksInboundTag)
                            put("listen", options.listenAddress)
                            put("port", options.socksPort)
                            put("protocol", "socks")
                            put(
                                "settings",
                                buildJsonObject {
                                    put("auth", "noauth")
                                    put("udp", true)
                                },
                            )
                        },
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
                    proxyOutbounds.forEach(::add)
                    add(
                        buildFreedomOutbound(
                            tag = DirectTag,
                            domainStrategy = xrayDirectOutboundDomainStrategy(
                                enableIpv6 = ipv6Enabled,
                                enableIpv6Prefer = ipv6Preferred,
                            ),
                        ),
                    )
                    add(buildSimpleOutbound(BlockTag, XrayProtocols.BLACKHOLE))
                },
            )
            put(
                "routing",
                buildJsonObject {
                    put("domainStrategy", routeDomainStrategy)
                    putJsonArray("rules") { routingRules.forEach(::add) }
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
        return DesktopProfileXrayJson.encodeToString(config) + "\n"
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

private fun String?.toDesktopRouteDomainStrategyOrNull(): String? = when (this?.trim()?.lowercase()) {
    "asis", "as-is", "0" -> "AsIs"
    "ipifnonmatch", "ip-if-non-match", "1" -> "IPIfNonMatch"
    "ipondemand", "ip-on-demand", "2" -> "IPOnDemand"
    else -> null
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
