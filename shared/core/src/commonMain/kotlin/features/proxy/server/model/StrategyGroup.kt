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

/**
 * Strategies that may send consecutive connections of one site through different servers. A site
 * that binds its session to the client IP can break when its connections exit from several servers.
 */
fun strategyGroupSpreadsConnections(strategy: String): Boolean = when (strategy.trim().lowercase()) {
    "random", "roundrobin", "round-robin", "leastload", "least-load", "load-balance" -> true
    else -> false
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
    /** The single source group of older groups. New groups use [wholeGroupIds] and [filterGroupIds] instead. */
    var subscriptionGroupId: Int? = null,
    /** A regex on server names. It keeps selecting servers as subscriptions refresh, within [filterGroupIds]. */
    var filter: String = "",
    /** Explicit user-selected members; they add up with [wholeGroupIds] and the [filter] matches. */
    var proxyServerIds: List<Int> = emptyList(),
    /** Subscriptions (or the manual group) selected whole: every server they hold, now and after any refresh. */
    var wholeGroupIds: List<Int> = emptyList(),
    /** The groups the [filter] looks at; empty means every group. */
    var filterGroupIds: List<Int> = emptyList(),
    /** User-selected member for `select`, or a transient verified startup member for automatic groups. */
    var selectedMemberId: Int? = null,
    /** Display policy for home proxy groups list. */
    var displayMode: String = StrategyGroupDisplayMode.ALWAYS,
    /** Hidden groups still work in routing, but are omitted from the main Proxy groups tab. */
    var showInAutoBalancerList: Boolean = true,
    /** Config-owned groups keep resolving their current named members after subscription refreshes. */
    var sourceTrafficConfigId: Int? = null,
    var sourcePolicyGroupName: String = "",
    /**
     * Xray switches a balancer away from a dead member only after the next probe round, so a long
     * interval leaves every new connection failing for that long. 15 s keeps the outage short.
     */
    var probeInterval: String = "15s",
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
            subscriptionGroupId?.toString() ?: if (wholeGroupIds.isNotEmpty()) "groups (${wholeGroupIds.size})" else "all"
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
        wholeGroupIds = other.wholeGroupIds
        filterGroupIds = other.filterGroupIds
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
        if (!hasMemberSource()) addIssue(ProxyServerValidationError.StrategyGroupMembersRequired)
    }

    override fun connectionFingerprint(): String {
        return "strategy|$remarks|$strategy|$subscriptionGroupId|$proxyServerIds|$wholeGroupIds|$filter|$filterGroupIds|$selectedMemberId|$displayMode|$tolerance"
    }
}

/**
 * Whether the group says where its members come from: chosen servers, a subscription, a name filter or a
 * profile's policy group. Without any of them it has no members on any platform.
 */
fun StrategyGroup.hasMemberSource(): Boolean =
    proxyServerIds.isNotEmpty() || wholeGroupIds.isNotEmpty() || subscriptionGroupId != null ||
        filter.isNotBlank() || sourceTrafficConfigId != null

/** The groups taken whole; an older group that named one source group and no filter meant that group whole. */
fun StrategyGroup.effectiveWholeGroupIds(): List<Int> =
    (wholeGroupIds + listOfNotNull(subscriptionGroupId?.takeIf { filter.isBlank() })).distinct()

/** The groups the filter looks at; an older group's single source group narrowed its filter the same way. */
fun StrategyGroup.effectiveFilterGroupIds(): List<Int> =
    (filterGroupIds + listOfNotNull(subscriptionGroupId?.takeIf { filter.isNotBlank() })).distinct()
