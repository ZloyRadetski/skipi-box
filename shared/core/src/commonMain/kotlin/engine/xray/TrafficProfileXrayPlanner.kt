// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.config.ShadowrocketConfigAnalysis
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketRule
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.config.TrafficConfigReference
import features.config.analyzeShadowrocketConfig
import features.config.toXrayRouteRule
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.isCompositeProxyServer
import features.proxy.server.model.stripLeadingCountryFlag
import features.proxy.server.model.toXrayBalancerStrategy
import features.routing.model.RouteRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    /** Servers behind [proxyOutbounds], used for DNS bootstrap domains. */
    val proxyServers: List<ProxyServer<*>>,
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
        configs: List<TrafficConfigReference> = emptyList(),
        /** Android's strict full tunnel: Direct rules are switched off and a Direct FINAL goes to the proxy. */
        strictFullTunnel: Boolean = false,
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
        val proxyServers = mutableListOf<ProxyServer<*>>()
        fun outboundTagFor(candidate: TrafficProfileServerRecord): String =
            if (candidate.id == selected.id) ProfileProxyTag else "$ProfileServerTagPrefix${candidate.id}"
        fun addOutbound(candidate: TrafficProfileServerRecord, tag: String) {
            if (addedOutboundTags.add(tag)) {
                proxyOutbounds += candidate.server.toXrayOutbound(tag).toJsonObject()
                proxyServers += candidate.server
            }
        }
        fun normalTarget(candidate: TrafficProfileServerRecord): XrayRouteTarget {
            val tag = outboundTagFor(candidate)
            addOutbound(candidate, tag)
            return XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
        }

        addOutbound(selected, ProfileProxyTag)
        val builtGroupTargets = mutableMapOf<String, XrayRouteTarget>()
        val buildingGroups = mutableSetOf<String>()
        val balancers = mutableListOf<XrayBalancerPlan>()
        val observatorySelectors = mutableListOf<String>()
        var observatoryUrl: String? = null
        var observatoryInterval: String? = null

        fun membersFor(
            group: ShadowrocketPolicyGroup,
            policyGroups: List<ShadowrocketPolicyGroup>,
            visited: Set<String> = emptySet(),
        ): List<TrafficProfileServerRecord> {
            val groupKey = group.name.trim().lowercase()
            if (groupKey in visited) return emptyList()
            val groupsByName = policyGroups.associateBy { it.name.trim().lowercase() }
            return group.members.flatMap { rawMember ->
                val member = rawMember.trim().removeSurrounding("\"").removeSurrounding("'")
                when {
                    member == ".*" -> usableServers
                    else -> groupsByName[member.lowercase()]?.let { nested ->
                        membersFor(nested, policyGroups, visited + groupKey)
                    }
                        ?: usableServers.filter { it.server.getInfo().remarks.matchesProfileGroupMember(member) }
                }
            }.distinctBy(TrafficProfileServerRecord::id)
        }

        fun groupTarget(
            group: ShadowrocketPolicyGroup,
            policyGroups: List<ShadowrocketPolicyGroup>,
            namespace: String,
        ): XrayRouteTarget {
            val groupKey = "$namespace:${group.name.trim().lowercase()}"
            builtGroupTargets[groupKey]?.let { return it }
            check(buildingGroups.add(groupKey)) { "Proxy groups form a cycle at '${group.name}'" }
            try {
                val members = membersFor(group, policyGroups)
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

        fun policyTarget(
            policy: String,
            policyGroups: List<ShadowrocketPolicyGroup> = analysis.proxyGroups,
            namespace: String = "active",
            visitedConfigIds: Set<Int> = emptySet(),
        ): XrayRouteTarget {
            val normalized = policy.trim()
            policyGroups.firstOrNull { group -> group.name.equals(normalized, ignoreCase = true) }
                ?.let { group -> return groupTarget(group, policyGroups, namespace) }
            when {
                normalized.equals("PROXY", ignoreCase = true) -> return XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
                normalized.equals("DIRECT", ignoreCase = true) -> return XrayRouteTarget(DirectTag, XrayRouteTargetKind.Outbound)
                normalized.equals("BLOCK", ignoreCase = true) || normalized.startsWith("REJECT", ignoreCase = true) -> {
                    return XrayRouteTarget(BlockTag, XrayRouteTargetKind.Outbound)
                }
                normalized.startsWith("CONFIG:", ignoreCase = true) -> {
                    val configId = normalized.substringAfter(':').trim().toIntOrNull()
                    val referenced = configId
                        ?.takeUnless(visitedConfigIds::contains)
                        ?.let { id -> configs.firstOrNull { config -> config.id == id } }
                    val referencedAnalysis = referenced?.rawConfig?.analyzeShadowrocketConfig()
                    val finalRule = referencedAnalysis?.rules?.lastOrNull(ShadowrocketRule::isFinal)
                    if (referencedAnalysis == null || finalRule == null) {
                        return XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
                    }
                    return policyTarget(
                        policy = finalRule.policy,
                        policyGroups = referencedAnalysis.proxyGroups,
                        namespace = "config:${referenced.id}",
                        visitedConfigIds = visitedConfigIds + referenced.id,
                    )
                }
            }
            usableServers.firstOrNull { it.server.getInfo().remarks.equals(normalized, ignoreCase = true) }
                ?.let(::normalTarget)?.let { return it }
            val tag = normalized.removePrefix(ShadowrocketPolicyGroupTagPrefix)
            return when {
                tag.equals("PROXY", ignoreCase = true) -> XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
                tag.equals("DIRECT", ignoreCase = true) -> XrayRouteTarget(DirectTag, XrayRouteTargetKind.Outbound)
                tag.equals("BLOCK", ignoreCase = true) || tag.startsWith("REJECT", ignoreCase = true) ->
                    XrayRouteTarget(BlockTag, XrayRouteTargetKind.Outbound)
                else -> XrayRouteTarget(tag, XrayRouteTargetKind.Outbound)
            }
        }

        val routingRules = buildList {
            analysis.rules.filterNot(ShadowrocketRule::isFinal)
                .mapNotNull { rule -> rule.toTrafficProfileXrayRule({ policy -> policyTarget(policy) }, capabilities) }
                .filterNot { rule -> strictFullTunnel && rule.isDirectRule() }
                .forEach(::add)
            val finalTarget = analysis.rules.lastOrNull(ShadowrocketRule::isFinal)
                ?.let { rule -> policyTarget(rule.policy) }
                ?: XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
            val effectiveFinalTarget = if (strictFullTunnel && finalTarget.isDirect()) {
                XrayRouteTarget(ProfileProxyTag, XrayRouteTargetKind.Outbound)
            } else {
                finalTarget
            }
            add(effectiveFinalTarget.toFinalTrafficProfileRule())
        }

        return TrafficProfileXrayPlan(
            analysis = analysis,
            selectedServer = selected,
            proxyOutbounds = proxyOutbounds,
            proxyServers = proxyServers,
            routingRules = routingRules,
            balancers = balancers,
            observatorySelectors = observatorySelectors,
            observatoryUrl = observatoryUrl,
            observatoryInterval = observatoryInterval,
        )
    }
}

private fun JsonObject.isDirectRule(): Boolean =
    (this["outboundTag"] as? JsonPrimitive)?.content?.trim().equals(DirectTag, ignoreCase = true)

private fun XrayRouteTarget.isDirect(): Boolean =
    kind == XrayRouteTargetKind.Outbound && tag.trim().equals(DirectTag, ignoreCase = true)

private fun ProxyServer<*>.isTrafficProfileUsable(): Boolean =
    this !is Custom && this !is StrategyGroup && !isCompositeProxyServer()

private fun XrayRouteTarget.toFinalTrafficProfileRule(): JsonObject = buildJsonObject {
    put("network", "tcp,udp")
    applyTo(this)
}

private fun ShadowrocketRule.toTrafficProfileXrayRule(
    resolveTarget: (String) -> XrayRouteTarget,
    capabilities: TrafficProfileXrayCapabilities,
): JsonObject? {
    val cleanValue = value.trim()
    val ruleType = type.trim().uppercase()

    var target: XrayRouteTarget? = null
    val route = toXrayRouteRule(id = 0) { policy ->
        resolveTarget(policy).also { target = it }.tag
    } ?: return null

    if (ruleType == "IP-ASN" && !capabilities.supportsIpAsnRules) {
        require(false) { capabilities.unsupportedIpAsnMessage(cleanValue) }
    }
    route.validateTrafficProfileRuleSetResources(capabilities)

    return buildJsonObject {
        if (route.domain.isNotEmpty()) putJsonArray("domain") { route.domain.forEach(::add) }
        if (route.ip.isNotEmpty()) putJsonArray("ip") { route.ip.forEach(::add) }
        if (route.process.isNotEmpty()) putJsonArray("process") { route.process.forEach(::add) }
        if (route.port.isNotBlank()) put("port", route.port)
        if (route.network.isNotBlank()) put("network", route.network)
        if (route.protocol.isNotBlank()) putJsonArray("protocol") { add(route.protocol) }
        requireNotNull(target).applyTo(this)
    }
}

private fun String.matchesProfileGroupMember(member: String): Boolean {
    val remarks = trim()
    val cleanRemarks = remarks.stripLeadingCountryFlag().trim()
    val cleanMember = member.stripLeadingCountryFlag().trim()
    return remarks.equals(member, ignoreCase = true) || cleanRemarks.equals(member, ignoreCase = true) ||
        cleanRemarks.equals(cleanMember, ignoreCase = true) || remarks.equals(cleanMember, ignoreCase = true)
}

private fun RouteRule.validateTrafficProfileRuleSetResources(
    capabilities: TrafficProfileXrayCapabilities,
) {
    // The converter's match fields determine whether a value is a resource reference.
    val externalReferences = (domain + ip)
        .mapNotNull(String::toTrafficProfileExternalRuleSetReference)
        .distinctBy { (source, _) -> source.lowercase() }

    externalReferences.forEach { (source, dataName) ->
        require(capabilities.supportedExternalRuleSetFiles.any { it.equals(source, ignoreCase = true) }) {
            capabilities.unsupportedExternalRuleSetMessage(source, dataName)
        }
    }
}

private fun String.toTrafficProfileExternalRuleSetReference(): Pair<String, String>? {
    if (!startsWith("ext:", ignoreCase = true)) return null
    val source = substringAfter(':').substringBefore(':').trim().takeIf(String::isNotEmpty) ?: return null
    val dataName = if (source.equals("geoip.dat", ignoreCase = true)) "geoip" else "geosite"
    return source to dataName
}

const val ProfileProxyTag = "skipi-proxy"
private const val ProfileServerTagPrefix = "skipi-server-"
private const val ProfileGroupTagPrefix = "skipi-group-"
private const val ProfileGroupMemberTagPrefix = "skipi-group-member-"
private const val DirectTag = "direct"
private const val BlockTag = "block"
