// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.config.ShadowrocketConfigAnalysis
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketRule
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.isCompositeProxyServer
import features.proxy.server.model.stripLeadingCountryFlag
import features.proxy.server.model.toXrayBalancerStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class TrafficProfileServerRecord(val id: Int, val server: ProxyServer<*>)

data class TrafficProfileXrayCapabilities(
    val supportsIpAsnRules: Boolean = true,
    val supportedExternalRuleSetFiles: Set<String> = emptySet(),
    val unsupportedIpAsnMessage: (String) -> String = { value -> "IP-ASN rule '$value' is unsupported" },
    val externalRuleSetReferenceMessage: (String) -> String = { value -> "External rule-set reference '$value' is unsupported" },
    val unsupportedExternalRuleSetMessage: (String, String) -> String = { source, data ->
        "External rule-set '$source' cannot be used as $data data"
    },
)

data class TrafficProfileXrayPlan(
    val analysis: ShadowrocketConfigAnalysis,
    val selectedServer: TrafficProfileServerRecord,
    val proxyOutbounds: List<JsonObject>,
    val routingRules: List<JsonObject>,
    val balancers: List<XrayBalancerPlan>,
    val observatorySelectors: List<String>,
    val observatoryUrl: String?,
    val observatoryInterval: String?,
)

/** Compiles profile policies and local server groups into portable Xray outbounds and routes. */
object TrafficProfileXrayPlanner {
    fun plan(
        profileContent: String,
        servers: List<TrafficProfileServerRecord>,
        selectedServerId: Int?,
        capabilities: TrafficProfileXrayCapabilities = TrafficProfileXrayCapabilities(),
    ): TrafficProfileXrayPlan {
        val analysis = profileContent.analyzeShadowrocketConfig()
        analysis.diagnostics.firstOrNull { it.severity.name == "Error" }?.let { diagnostic ->
            error("Active profile has an error: ${diagnostic.message}")
        }
        val selected = selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }
            ?: error("Select a server before connecting")
        require(selected.server.isTrafficProfileUsable()) {
            "Selected server '${selected.server.getInfo().remarks.trim()}' cannot be used with a traffic profile"
        }
        require(selected.server.validateFull().isEmpty()) {
            "Selected server '${selected.server.getInfo().remarks.trim()}' is invalid"
        }

        val usableServers = servers.filter { record -> record.server.isTrafficProfileUsable() }
        val addedOutboundTags = mutableSetOf<String>()
        val proxyOutbounds = mutableListOf<JsonObject>()
        fun outboundTagFor(candidate: TrafficProfileServerRecord): String =
            if (candidate.id == selected.id) ProfileProxyTag else "$ProfileServerTagPrefix${candidate.id}"
        fun addOutbound(candidate: TrafficProfileServerRecord, tag: String) {
            if (addedOutboundTags.add(tag)) proxyOutbounds += candidate.server.toXrayOutbound(tag).toJsonObject()
        }
        fun normalTarget(candidate: TrafficProfileServerRecord): XrayRouteTarget {
            val tag = outboundTagFor(candidate)
            addOutbound(candidate, tag)
            return XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
        }

        addOutbound(selected, ProfileProxyTag)
        val groupsByName = analysis.proxyGroups.associateBy { it.name.trim().lowercase() }
        val builtGroupTargets = mutableMapOf<String, XrayRouteTarget>()
        val buildingGroups = mutableSetOf<String>()
        val balancers = mutableListOf<XrayBalancerPlan>()
        val observatorySelectors = mutableListOf<String>()
        var observatoryUrl: String? = null
        var observatoryInterval: String? = null

        fun membersFor(group: ShadowrocketPolicyGroup, visited: Set<String> = emptySet()): List<TrafficProfileServerRecord> {
            val groupKey = group.name.trim().lowercase()
            if (groupKey in visited) return emptyList()
            return group.members.flatMap { rawMember ->
                val member = rawMember.trim().removeSurrounding("\"").removeSurrounding("'")
                when {
                    member == ".*" -> usableServers
                    else -> groupsByName[member.lowercase()]?.let { nested -> membersFor(nested, visited + groupKey) }
                        ?: usableServers.filter { it.server.getInfo().remarks.matchesProfileGroupMember(member) }
                }
            }.distinctBy(TrafficProfileServerRecord::id)
        }

        fun groupTarget(group: ShadowrocketPolicyGroup): XrayRouteTarget {
            val groupKey = group.name.trim().lowercase()
            builtGroupTargets[groupKey]?.let { return it }
            check(buildingGroups.add(groupKey)) { "Proxy groups form a cycle at '${group.name}'" }
            try {
                val members = membersFor(group)
                check(members.isNotEmpty()) { "Proxy group '${group.name}' has no matching local servers" }
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
                        balancers += XrayBalancerPlan(
                            tag = balancerTag,
                            selector = selectorPrefix,
                            strategy = group.type.toXrayBalancerStrategy(),
                            fallbackTag = memberTags.first(),
                        )
                        observatorySelectors += selectorPrefix
                        if (observatoryUrl == null) observatoryUrl = group.url.trim().takeIf(String::isNotBlank)
                        if (observatoryInterval == null) {
                            observatoryInterval = group.intervalSeconds?.takeIf { it > 0 }?.let { "${it}s" }
                        }
                        XrayRouteTarget(balancerTag, XrayRouteTargetKind.Balancer)
                    }
                }
                builtGroupTargets[groupKey] = target
                return target
            } finally {
                buildingGroups -= groupKey
            }
        }

        fun policyTarget(policy: String): XrayRouteTarget {
            val normalized = policy.trim()
            when {
                normalized.equals("PROXY", ignoreCase = true) -> return XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
                normalized.equals("DIRECT", ignoreCase = true) -> return XrayRouteTarget(DirectTag, XrayRouteTargetKind.Outbound)
                normalized.equals("BLOCK", ignoreCase = true) || normalized.startsWith("REJECT", ignoreCase = true) -> {
                    return XrayRouteTarget(BlockTag, XrayRouteTargetKind.Outbound)
                }
            }
            groupsByName[normalized.lowercase()]?.let(::groupTarget)?.let { return it }
            usableServers.firstOrNull { it.server.getInfo().remarks.equals(normalized, ignoreCase = true) }
                ?.let(::normalTarget)?.let { return it }
            error("Profile policy '$normalized' does not match a server or [Proxy Group]")
        }

        val routingRules = buildList {
            analysis.rules.filterNot(ShadowrocketRule::isFinal)
                .mapNotNull { rule -> rule.toTrafficProfileXrayRule(policyTarget(rule.policy), capabilities) }
                .forEach(::add)
            val finalTarget = analysis.rules.lastOrNull(ShadowrocketRule::isFinal)
                ?.let { rule -> policyTarget(rule.policy) }
                ?: XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
            add(finalTarget.toFinalTrafficProfileRule())
        }

        return TrafficProfileXrayPlan(
            analysis = analysis,
            selectedServer = selected,
            proxyOutbounds = proxyOutbounds,
            routingRules = routingRules,
            balancers = balancers,
            observatorySelectors = observatorySelectors,
            observatoryUrl = observatoryUrl,
            observatoryInterval = observatoryInterval,
        )
    }
}

private fun ProxyServer<*>.isTrafficProfileUsable(): Boolean =
    this !is Custom && this !is StrategyGroup && !isCompositeProxyServer()

private fun XrayRouteTarget.toFinalTrafficProfileRule(): JsonObject = buildJsonObject {
    put("network", "tcp,udp")
    applyTo(this)
}

private fun ShadowrocketRule.toTrafficProfileXrayRule(
    target: XrayRouteTarget,
    capabilities: TrafficProfileXrayCapabilities,
): JsonObject? {
    val cleanValue = value.trim()
    val ruleType = type.trim().uppercase()
    if (ruleType == "IP-ASN" && !capabilities.supportsIpAsnRules) {
        require(false) { capabilities.unsupportedIpAsnMessage(cleanValue) }
    }
    if (ruleType == "RULE-SET" && '/' in cleanValue && !cleanValue.startsWith("geoip:", ignoreCase = true)) {
        error(capabilities.externalRuleSetReferenceMessage(cleanValue))
    }
    val domain = when (ruleType) {
        "DOMAIN" -> listOf("full:$cleanValue")
        "DOMAIN-SUFFIX" -> listOf("domain:$cleanValue")
        "DOMAIN-KEYWORD" -> listOf("keyword:$cleanValue")
        "DOMAIN-WILDCARD" -> listOf("regexp:${cleanValue.toShadowrocketWildcardRegex()}")
        "DOMAIN-SET", "GEOSITE" -> listOf(cleanValue.toTrafficProfileDomainSetValue(capabilities))
        "RULE-SET" -> cleanValue.takeIf { !it.isTrafficProfileIpRuleSet() && '/' !in it }
            ?.let { listOf(it.toTrafficProfileDomainSetValue(capabilities)) }.orEmpty()
        else -> emptyList()
    }
    val ip = when (ruleType) {
        "IP-CIDR", "IP-CIDR6" -> listOf(cleanValue)
        "IP-ASN" -> cleanValue.removePrefix("AS").removePrefix("as").trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.let { listOf("geoip:as$it") }.orEmpty()
        "GEOIP" -> listOf(cleanValue.toTrafficProfileGeoIpValue(capabilities))
        "RULE-SET" -> cleanValue.takeIf { it.isTrafficProfileIpRuleSet() || '/' in it }
            ?.let { listOf(it.toTrafficProfileGeoIpValue(capabilities)) }.orEmpty()
        else -> emptyList()
    }
    val process = cleanValue.takeIf { ruleType == "PROCESS-NAME" }?.let(::listOf).orEmpty()
    val port = cleanValue.takeIf { ruleType == "DST-PORT" }.orEmpty()
    val network = cleanValue.takeIf { ruleType == "NETWORK" }.orEmpty()
    val protocol = cleanValue.takeIf { ruleType == "PROTOCOL" }.orEmpty()
    if (domain.isEmpty() && ip.isEmpty() && process.isEmpty() && port.isBlank() && network.isBlank() && protocol.isBlank()) return null
    return buildJsonObject {
        if (domain.isNotEmpty()) putJsonArray("domain") { domain.forEach(::add) }
        if (ip.isNotEmpty()) putJsonArray("ip") { ip.forEach(::add) }
        if (process.isNotEmpty()) putJsonArray("process") { process.forEach(::add) }
        if (port.isNotBlank()) put("port", port)
        if (network.isNotBlank()) put("network", network)
        if (protocol.isNotBlank()) putJsonArray("protocol") { add(protocol) }
        target.applyTo(this)
    }
}

private fun String.matchesProfileGroupMember(member: String): Boolean {
    val remarks = trim()
    val cleanRemarks = remarks.stripLeadingCountryFlag().trim()
    val cleanMember = member.stripLeadingCountryFlag().trim()
    return remarks.equals(member, ignoreCase = true) || cleanRemarks.equals(member, ignoreCase = true) ||
        cleanRemarks.equals(cleanMember, ignoreCase = true) || remarks.equals(cleanMember, ignoreCase = true)
}

private fun String.toTrafficProfileDomainSetValue(capabilities: TrafficProfileXrayCapabilities): String {
    val normalized = if (startsWith("ext-site:", ignoreCase = true)) "ext:${substringAfter(':')}" else this
    return when {
        normalized.startsWith("ext:", ignoreCase = true) -> normalized.requireTrafficProfileRuleSet("geosite.dat", "geosite", capabilities)
        normalized.startsWith("geosite:", ignoreCase = true) || normalized.startsWith("domain:", ignoreCase = true) ||
            normalized.startsWith("full:", ignoreCase = true) || normalized.startsWith("keyword:", ignoreCase = true) ||
            normalized.startsWith("regexp:", ignoreCase = true) -> normalized
        else -> "geosite:$normalized"
    }
}

private fun String.toTrafficProfileGeoIpValue(capabilities: TrafficProfileXrayCapabilities): String {
    val normalized = if (startsWith("ext-ip:", ignoreCase = true)) "ext:${substringAfter(':')}" else this
    return when {
        normalized.startsWith("ext:", ignoreCase = true) -> normalized.requireTrafficProfileRuleSet("geoip.dat", "geoip", capabilities)
        normalized.startsWith("geoip:", ignoreCase = true) -> normalized
        else -> "geoip:$normalized"
    }
}

private fun String.requireTrafficProfileRuleSet(
    expectedFileName: String,
    dataName: String,
    capabilities: TrafficProfileXrayCapabilities,
): String {
    val source = substringAfter(':').substringBefore(':').trim()
    require(
        source.equals(expectedFileName, ignoreCase = true) &&
            capabilities.supportedExternalRuleSetFiles.any { it.equals(source, ignoreCase = true) },
    ) {
        capabilities.unsupportedExternalRuleSetMessage(source, dataName)
    }
    return this
}

private fun String.isTrafficProfileIpRuleSet(): Boolean =
    startsWith("geoip:", ignoreCase = true) || startsWith("ext-ip:", ignoreCase = true) ||
        startsWith("ext:geoip.dat:", ignoreCase = true)

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
private const val DirectTag = "direct"
private const val BlockTag = "block"
