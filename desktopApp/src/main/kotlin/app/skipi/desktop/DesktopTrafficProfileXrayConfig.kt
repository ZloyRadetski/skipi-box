// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketRule
import features.config.analyzeShadowrocketConfig
import features.config.shadowrocketSectionValue
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.stripLeadingCountryFlag
import features.proxy.server.model.isCompositeProxyServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

        val analysis = profile.content.analyzeShadowrocketConfig()
        analysis.diagnostics.firstOrNull { diagnostic -> diagnostic.severity.name == "Error" }?.let { diagnostic ->
            error("Active profile has an error: ${diagnostic.message}")
        }

        val decodedServers = serverLibrary.servers.mapNotNull { stored ->
            stored.decode().getOrNull()?.let { server -> DesktopProfileServer(stored, server) }
        }
        val selected = serverLibrary.selectedServerId
            ?.let { selectedId -> decodedServers.firstOrNull { candidate -> candidate.id == selectedId } }
            ?: error("Select a server before connecting")
        require(selected.isUsable) {
            "Selected server '${selected.remarks}' cannot be used with a traffic profile"
        }
        require(selected.server.validateFull().isEmpty()) {
            "Selected server '${selected.remarks}' is invalid"
        }

        val usableServers = decodedServers.filter(DesktopProfileServer::isUsable)
        val addedOutboundTags = mutableSetOf<String>()
        val proxyOutbounds = mutableListOf<JsonObject>()

        fun outboundTagFor(candidate: DesktopProfileServer): String =
            if (candidate.id == selected.id) ProfileProxyTag else "$ProfileServerTagPrefix${candidate.id}"

        fun addOutbound(candidate: DesktopProfileServer, tag: String) {
            if (addedOutboundTags.add(tag)) {
                proxyOutbounds += candidate.server.toXrayOutbound(tag).toJsonObject()
            }
        }

        fun normalTarget(candidate: DesktopProfileServer): DesktopProfileRouteTarget {
            val tag = outboundTagFor(candidate)
            addOutbound(candidate, tag)
            return DesktopProfileRouteTarget(outboundTag = tag)
        }

        addOutbound(selected, ProfileProxyTag)

        val groupsByName = analysis.proxyGroups.associateBy { group -> group.name.trim().lowercase() }
        val builtGroupTargets = mutableMapOf<String, DesktopProfileRouteTarget>()
        val buildingGroups = mutableSetOf<String>()
        val balancers = mutableListOf<JsonObject>()
        val observatorySelectors = mutableListOf<String>()
        var observatoryUrl: String? = null
        var observatoryInterval: String? = null

        fun membersFor(group: ShadowrocketPolicyGroup, visited: Set<String> = emptySet()): List<DesktopProfileServer> {
            val groupKey = group.name.trim().lowercase()
            if (groupKey in visited) return emptyList()
            return group.members.flatMap { rawMember ->
                val member = rawMember.trim().removeSurrounding("\"").removeSurrounding("'")
                when {
                    member == ".*" -> usableServers
                    else -> {
                        val nested = groupsByName[member.lowercase()]
                        if (nested != null) {
                            membersFor(nested, visited + groupKey)
                        } else {
                            usableServers.filter { candidate ->
                                candidate.remarks.matchesProfileGroupMember(member)
                            }
                        }
                    }
                }
            }.distinctBy(DesktopProfileServer::id)
        }

        fun groupTarget(group: ShadowrocketPolicyGroup): DesktopProfileRouteTarget {
            val groupKey = group.name.trim().lowercase()
            builtGroupTargets[groupKey]?.let { return it }
            check(buildingGroups.add(groupKey)) { "Proxy groups form a cycle at '${group.name}'" }
            try {
                val members = membersFor(group)
                check(members.isNotEmpty()) {
                    "Proxy group '${group.name}' has no matching local servers"
                }
                val target = when (group.type.trim().lowercase()) {
                    "select" -> normalTarget(members.first())
                    else -> {
                        val groupId = builtGroupTargets.size + 1
                        val selectorPrefix = "$ProfileGroupMemberTagPrefix$groupId-"
                        val memberTags = members.map { member ->
                            val tag = "$selectorPrefix${member.id}"
                            addOutbound(member, tag)
                            tag
                        }
                        val balancerTag = "$ProfileGroupTagPrefix$groupId"
                        balancers += buildJsonObject {
                            put("tag", balancerTag)
                            putJsonArray("selector") { add(selectorPrefix) }
                            put(
                                "strategy",
                                buildJsonObject {
                                    put("type", group.type.toXrayBalancerStrategy())
                                },
                            )
                            put("fallbackTag", memberTags.first())
                        }
                        observatorySelectors += selectorPrefix
                        if (observatoryUrl == null) {
                            observatoryUrl = group.url.trim().takeIf(String::isNotBlank)
                        }
                        if (observatoryInterval == null) {
                            observatoryInterval = group.intervalSeconds?.takeIf { seconds -> seconds > 0 }?.let { seconds -> "${seconds}s" }
                        }
                        DesktopProfileRouteTarget(balancerTag = balancerTag)
                    }
                }
                builtGroupTargets[groupKey] = target
                return target
            } finally {
                buildingGroups -= groupKey
            }
        }

        fun policyTarget(policy: String): DesktopProfileRouteTarget {
            val normalized = policy.trim()
            when {
                normalized.equals("PROXY", ignoreCase = true) -> return DesktopProfileRouteTarget(outboundTag = ProfileProxyTag)
                normalized.equals("DIRECT", ignoreCase = true) -> return DesktopProfileRouteTarget(outboundTag = DirectTag)
                normalized.equals("BLOCK", ignoreCase = true) || normalized.startsWith("REJECT", ignoreCase = true) -> {
                    return DesktopProfileRouteTarget(outboundTag = BlockTag)
                }
            }
            groupsByName[normalized.lowercase()]?.let(::groupTarget)?.let { return it }
            usableServers.firstOrNull { candidate -> candidate.remarks.equals(normalized, ignoreCase = true) }
                ?.let(::normalTarget)
                ?.let { return it }
            error("Profile policy '$normalized' does not match a server or [Proxy Group]")
        }

        val routingRules = buildList {
            analysis.rules
                .filterNot(ShadowrocketRule::isFinal)
                .mapNotNull { rule -> rule.toDesktopXrayRoutingRule(policyTarget(rule.policy)) }
                .forEach(::add)
            val finalTarget = analysis.rules.lastOrNull(ShadowrocketRule::isFinal)
                ?.let { rule -> policyTarget(rule.policy) }
                ?: DesktopProfileRouteTarget(outboundTag = ProfileProxyTag)
            add(finalTarget.toFinalRoutingRule())
        }

        val dnsServers = analysis.general["dns-server"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter { value -> value.isNotEmpty() && !value.equals("system", ignoreCase = true) }
            ?.distinct()
            .orEmpty()
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
                        buildJsonObject {
                            put("tag", DirectTag)
                            put("protocol", "freedom")
                            put(
                                "settings",
                                buildJsonObject {
                                    put(
                                        "domainStrategy",
                                        when {
                                            ipv6Enabled && ipv6Preferred -> "UseIPv6v4"
                                            ipv6Enabled -> "UseIP"
                                            else -> "UseIPv4"
                                        },
                                    )
                                },
                            )
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", BlockTag)
                            put("protocol", "blackhole")
                        },
                    )
                },
            )
            put(
                "routing",
                buildJsonObject {
                    put("domainStrategy", routeDomainStrategy)
                    putJsonArray("rules") { routingRules.forEach(::add) }
                    if (balancers.isNotEmpty()) {
                        putJsonArray("balancers") { balancers.forEach(::add) }
                    }
                },
            )
            if (observatorySelectors.isNotEmpty()) {
                put(
                    "observatory",
                    buildJsonObject {
                        putJsonArray("subjectSelector") { observatorySelectors.distinct().forEach(::add) }
                        put("probeURL", observatoryUrl ?: DefaultProbeUrl)
                        put("probeInterval", observatoryInterval ?: DefaultProbeInterval)
                        put("enableConcurrency", true)
                    },
                )
            }
        }
        return DesktopProfileXrayJson.encodeToString(config) + "\n"
    }
}

private data class DesktopProfileServer(
    val stored: DesktopStoredProxyServer,
    val server: ProxyServer<*>,
) {
    val id: Int get() = stored.id
    val remarks: String get() = server.getInfo().remarks.trim()
    val isUsable: Boolean get() = server !is Custom && server !is StrategyGroup && !server.isCompositeProxyServer()
}

private data class DesktopProfileRouteTarget(
    val outboundTag: String? = null,
    val balancerTag: String? = null,
) {
    fun toFinalRoutingRule(): JsonObject = buildJsonObject {
        put("network", "tcp,udp")
        putTarget(this@DesktopProfileRouteTarget)
    }
}

private fun ShadowrocketRule.toDesktopXrayRoutingRule(target: DesktopProfileRouteTarget): JsonObject? {
    val cleanValue = value.trim()
    val ruleType = type.trim().uppercase()
    require(ruleType != "IP-ASN") {
        "IP-ASN rule '$cleanValue' is not supported by the bundled Desktop Xray resources"
    }
    if (ruleType == "RULE-SET" && '/' in cleanValue && !cleanValue.startsWith("geoip:", ignoreCase = true)) {
        error("RULE-SET '$cleanValue' needs an external rule-set file, which Desktop SKIPI does not bundle")
    }
    val domain = when (ruleType) {
        "DOMAIN" -> listOf("full:$cleanValue")
        "DOMAIN-SUFFIX" -> listOf("domain:$cleanValue")
        "DOMAIN-KEYWORD" -> listOf("keyword:$cleanValue")
        "DOMAIN-WILDCARD" -> listOf("regexp:${cleanValue.toShadowrocketWildcardRegex()}")
        "DOMAIN-SET" -> listOf(cleanValue.toDesktopXrayDomainSetValue())
        "GEOSITE" -> listOf(cleanValue.toDesktopXrayDomainSetValue())
        "RULE-SET" -> cleanValue.takeIf { value -> !value.isDesktopXrayIpRuleSet() && '/' !in value }
            ?.let { value -> listOf(value.toDesktopXrayDomainSetValue()) }
            .orEmpty()
        else -> emptyList()
    }
    val ip = when (ruleType) {
        "IP-CIDR", "IP-CIDR6", "IP-ASN" -> listOf(cleanValue)
        "GEOIP" -> listOf(cleanValue.toDesktopXrayGeoIpValue())
        "RULE-SET" -> cleanValue.takeIf { value -> value.isDesktopXrayIpRuleSet() || '/' in value }
            ?.let { value -> listOf(value.toDesktopXrayGeoIpValue()) }
            .orEmpty()
        else -> emptyList()
    }
    val process = cleanValue.takeIf { ruleType == "PROCESS-NAME" }?.let(::listOf).orEmpty()
    val port = cleanValue.takeIf { ruleType == "DST-PORT" }.orEmpty()
    val network = cleanValue.takeIf { ruleType == "NETWORK" }.orEmpty()
    val protocol = cleanValue.takeIf { ruleType == "PROTOCOL" }.orEmpty()
    if (domain.isEmpty() && ip.isEmpty() && process.isEmpty() && port.isBlank() && network.isBlank() && protocol.isBlank()) {
        return null
    }
    return buildJsonObject {
        if (domain.isNotEmpty()) putJsonArray("domain") { domain.forEach(::add) }
        if (ip.isNotEmpty()) putJsonArray("ip") { ip.forEach(::add) }
        if (process.isNotEmpty()) putJsonArray("process") { process.forEach(::add) }
        if (port.isNotBlank()) put("port", port)
        if (network.isNotBlank()) put("network", network)
        if (protocol.isNotBlank()) putJsonArray("protocol") { add(protocol) }
        putTarget(target)
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putTarget(target: DesktopProfileRouteTarget) {
    if (target.balancerTag != null) {
        put("balancerTag", target.balancerTag)
    } else {
        put("outboundTag", checkNotNull(target.outboundTag))
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

private fun String.toXrayBalancerStrategy(): String = when (trim().lowercase()) {
    "load-balance", "random" -> "random"
    "round-robin", "roundrobin" -> "roundRobin"
    "least-load", "leastload" -> "leastLoad"
    "fallback", "url-test", "leastping" -> "leastPing"
    else -> "leastPing"
}

private fun String.toDesktopXrayDomainSetValue(): String {
    val normalized = if (startsWith("ext-site:", ignoreCase = true)) "ext:${substringAfter(':')}" else this
    return when {
        normalized.startsWith("ext:", ignoreCase = true) -> {
            normalized.requireDesktopBundledExtRuleSet(expectedFileName = "geosite.dat")
            normalized
        }
        normalized.startsWith("geosite:", ignoreCase = true) || normalized.startsWith("domain:", ignoreCase = true) ||
            normalized.startsWith("full:", ignoreCase = true) || normalized.startsWith("keyword:", ignoreCase = true) ||
            normalized.startsWith("regexp:", ignoreCase = true) -> normalized
        else -> "geosite:$normalized"
    }
}

private fun String.toDesktopXrayGeoIpValue(): String {
    val normalized = if (startsWith("ext-ip:", ignoreCase = true)) "ext:${substringAfter(':')}" else this
    return if (normalized.startsWith("ext:", ignoreCase = true)) {
        normalized.requireDesktopBundledExtRuleSet(expectedFileName = "geoip.dat")
        normalized
    } else if (normalized.startsWith("geoip:", ignoreCase = true)) {
        normalized
    } else {
        "geoip:$normalized"
    }
}

private fun String.requireDesktopBundledExtRuleSet(expectedFileName: String) {
    val source = substringAfter(':').substringBefore(':').trim()
    require(source.equals(expectedFileName, ignoreCase = true)) {
        "External rule-set '$source' cannot be used as ${expectedFileName.substringBefore('.')} data on Desktop SKIPI"
    }
}

private fun String.isDesktopXrayIpRuleSet(): Boolean =
    startsWith("geoip:", ignoreCase = true) ||
        startsWith("ext-ip:", ignoreCase = true) ||
        startsWith("ext:geoip.dat:", ignoreCase = true)

private fun String?.toConfigBooleanOrDefault(defaultValue: Boolean): Boolean = when (this?.trim()?.lowercase()) {
    "true", "yes", "1" -> true
    "false", "no", "0" -> false
    else -> defaultValue
}

private fun String.matchesProfileGroupMember(member: String): Boolean {
    val remarks = trim()
    val cleanRemarks = remarks.stripLeadingCountryFlag().trim()
    val cleanMember = member.stripLeadingCountryFlag().trim()
    return remarks.equals(member, ignoreCase = true) ||
        cleanRemarks.equals(member, ignoreCase = true) ||
        cleanRemarks.equals(cleanMember, ignoreCase = true) ||
        remarks.equals(cleanMember, ignoreCase = true)
}

private fun String?.toDesktopRouteDomainStrategyOrNull(): String? = when (this?.trim()?.lowercase()) {
    "asis", "as-is", "0" -> "AsIs"
    "ipifnonmatch", "ip-if-non-match", "1" -> "IPIfNonMatch"
    "ipondemand", "ip-on-demand", "2" -> "IPOnDemand"
    else -> null
}

private fun String.toShadowrocketWildcardRegex(): String = buildString {
    append('^')
    this@toShadowrocketWildcardRegex.forEach { character ->
        when (character) {
            '*' -> append(".*")
            '?' -> append('.')
            '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> append('\\').append(character)
            else -> append(character)
        }
    }
    append('$')
}

private const val ProfileProxyTag = "skipi-proxy"
private const val ProfileServerTagPrefix = "skipi-server-"
private const val ProfileGroupTagPrefix = "skipi-group-"
private const val ProfileGroupMemberTagPrefix = "skipi-group-member-"
private const val SocksInboundTag = "skipi-socks"
private const val DirectTag = "direct"
private const val BlockTag = "block"
private const val DefaultProbeUrl = "https://www.gstatic.com/generate_204"
private const val DefaultProbeInterval = "10s"

private val DesktopProfileXrayJson = Json { encodeDefaults = true }
