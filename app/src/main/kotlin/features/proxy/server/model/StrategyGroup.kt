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

object StrategyGroupDisplayMode {
    const val NEVER = "never"
    const val ALWAYS = "always"
    const val ACTIVE_CONFIG = "active_config"

    val MODES = listOf(ALWAYS, ACTIVE_CONFIG, NEVER)
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
    var probeInterval: String = "15s",
    var probeUrl: String = "",
    /** Prefer parallel health probes on startup; users can disable this for low-power networks. */
    var enableBurstProbe: Boolean = true,
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
