// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlinx.serialization.Serializable

object StrategyGroupConstants {
    const val TYPE_SELECT = "select"
    const val TYPE_LEAST_PING = "leastPing"
    const val TYPE_FALLBACK = "fallback"
    const val TYPE_LEAST_LOAD = "leastLoad"
    const val TYPE_RANDOM = "random"
    const val TYPE_ROUND_ROBIN = "roundRobin"

    val TYPES = setOf(
        TYPE_SELECT,
        TYPE_LEAST_PING,
        TYPE_FALLBACK,
        TYPE_LEAST_LOAD,
        TYPE_RANDOM,
        TYPE_ROUND_ROBIN,
    )
}

/** Maps a Shadowrocket [Proxy Group] policy name to SKIPI's stored strategy. */
fun String.toStrategyGroupTypeFromShadowrocketPolicy(): String = when (trim().lowercase()) {
    "select" -> StrategyGroupConstants.TYPE_SELECT
    "load-balance", "random" -> StrategyGroupConstants.TYPE_RANDOM
    "round-robin", "roundrobin" -> StrategyGroupConstants.TYPE_ROUND_ROBIN
    "least-load", "leastload" -> StrategyGroupConstants.TYPE_LEAST_LOAD
    "fallback" -> StrategyGroupConstants.TYPE_FALLBACK
    "url-test", "leastping" -> StrategyGroupConstants.TYPE_LEAST_PING
    else -> StrategyGroupConstants.TYPE_SELECT
}

/** Writes a SKIPI strategy back to the equivalent Shadowrocket policy name. */
fun String.toShadowrocketPolicyGroupType(): String = when (trim()) {
    StrategyGroupConstants.TYPE_SELECT -> "select"
    StrategyGroupConstants.TYPE_LEAST_PING -> "url-test"
    StrategyGroupConstants.TYPE_FALLBACK -> "fallback"
    StrategyGroupConstants.TYPE_LEAST_LOAD -> "least-load"
    StrategyGroupConstants.TYPE_RANDOM -> "load-balance"
    StrategyGroupConstants.TYPE_ROUND_ROBIN -> "round-robin"
    else -> "select"
}

/** Converts either a Shadowrocket policy name or stored strategy to Xray's balancer strategy. */
fun String.toXrayBalancerStrategy(): String = when (trim().lowercase()) {
    "load-balance", StrategyGroupConstants.TYPE_RANDOM -> StrategyGroupConstants.TYPE_RANDOM
    "round-robin", "roundrobin" -> StrategyGroupConstants.TYPE_ROUND_ROBIN
    "least-load", "leastload" -> StrategyGroupConstants.TYPE_LEAST_LOAD
    "fallback", "url-test", "leastping" -> StrategyGroupConstants.TYPE_LEAST_PING
    else -> StrategyGroupConstants.TYPE_LEAST_PING
}

@Serializable
data class StrategyGroup(
    var remarks: String = "",
    var strategy: String = StrategyGroupConstants.TYPE_SELECT,
    var subscriptionGroupId: Int? = null,
    var filter: String = "",
    /** Explicit user-selected members. Empty retains the legacy group/filter selector. */
    var proxyServerIds: List<Int> = emptyList(),
    /** User-selected member for `select`, or a transient verified startup member for automatic groups. */
    var selectedMemberId: Int? = null,
    /** Display policy for home proxy groups list. */
    var displayMode: String = StrategyGroupDisplayMode.ALWAYS,
    /** Hidden groups still work in routing, but are omitted from the main Proxy groups tab. */
    var showInAutoBalancerList: Boolean = true,
    /** Config-owned groups keep resolving their current named members after subscription refreshes. */
    var sourceTrafficConfigId: Int? = null,
    var sourcePolicyGroupName: String = "",
    /** One minute is responsive enough for ordinary failover without a constant radio wakeup. */
    var probeInterval: String = "1m",
    var probeUrl: String = "",
    /** An explicit opt-in for the expensive first-start parallel probe race. */
    var enableBurstProbe: Boolean = false,
    var tolerance: String = "50ms",
    var probeTimeout: String = "5s",
) : ProxyServer<StrategyGroup> {
    override fun getInfo(): ProxyServerInfo {
        val source = if (proxyServerIds.isNotEmpty()) {
            "custom (${proxyServerIds.size})"
        } else {
            subscriptionGroupId?.toString() ?: "all"
        }
        val filterText = filter.takeIf(String::isNotBlank)?.let { ", $it" }.orEmpty()
        return ProxyServerInfo(remarks, "$strategy, $source$filterText", "Strategy")
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        throw UnsupportedOperationException("Strategy groups are converted by XrayConfigFactory")
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is StrategyGroup) {
            proxyServerTypeMismatch()
        }
        remarks = other.remarks
        strategy = other.strategy
        subscriptionGroupId = other.subscriptionGroupId
        filter = other.filter
        proxyServerIds = other.proxyServerIds
        selectedMemberId = other.selectedMemberId
        displayMode = other.displayMode
        showInAutoBalancerList = other.showInAutoBalancerList
        sourceTrafficConfigId = other.sourceTrafficConfigId
        sourcePolicyGroupName = other.sourcePolicyGroupName
        probeInterval = other.probeInterval
        probeUrl = other.probeUrl
        enableBurstProbe = other.enableBurstProbe
        tolerance = other.tolerance
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = validateFull()

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        validateRemarks(remarks)
        validateAllowed(strategy, "strategy group type", StrategyGroupConstants.TYPES)
    }

    override fun connectionFingerprint(): String {
        return "strategy|$remarks|$strategy|$subscriptionGroupId|$proxyServerIds|$selectedMemberId|$displayMode|$tolerance"
    }
}
