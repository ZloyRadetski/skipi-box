// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.toShadowrocketPolicyGroupType
import features.proxy.server.model.toStrategyGroupTypeFromShadowrocketPolicy

/** A persisted server ID paired with the profile-facing name used by `[Proxy Group]`. */
data class ProxyGroupServerChoice(
    val id: Int,
    val rawName: String,
)

/** Renders an editable SKIPI strategy as a portable Shadowrocket proxy-group line. */
fun StrategyGroup.toShadowrocketLine(serverChoices: List<ProxyGroupServerChoice>): String {
    val type = strategy.toShadowrocketPolicyGroupType()
    val memberNames = if (proxyServerIds.isNotEmpty()) {
        proxyServerIds.mapNotNull { id ->
            serverChoices.firstOrNull { it.id == id }?.rawName
        }
    } else {
        emptyList()
    }
    val intervalSeconds = probeInterval.trim().removeSuffix("s").toIntOrNull()
        ?: if (probeInterval.endsWith("m")) probeInterval.removeSuffix("m").toIntOrNull()?.times(60) else null
    val timeoutSeconds = probeTimeout.trim().removeSuffix("s").toIntOrNull()?.takeIf { it in 1..30 }

    return buildString {
        append(remarks.trim()).append(" = ").append(type)
        memberNames.forEach { member -> append(", ").append(member) }
        if (strategy != StrategyGroupConstants.TYPE_SELECT) {
            probeUrl.trim().takeIf(String::isNotBlank)?.let { append(", url=").append(it) }
            intervalSeconds?.takeIf { it > 0 }?.let { append(", interval=").append(it) }
            timeoutSeconds?.takeIf { it != 5 }?.let { append(", timeout=").append(it) }
        }
        when (displayMode) {
            StrategyGroupDisplayMode.ALWAYS -> append(", skipi-display=always")
            StrategyGroupDisplayMode.ACTIVE_CONFIG -> append(", skipi-display=active_config")
            StrategyGroupDisplayMode.NEVER -> append(", skipi-display=never")
        }
        append(", skipi-burst-probe=").append(enableBurstProbe)
        tolerance.takeIf { it != "50ms" }?.let { value ->
            append(", skipi-tolerance=").append(value)
        }
    }
}

/**
 * Converts a parsed `[Proxy Group]` line to the editable SKIPI strategy model.
 * The caller provides its platform-specific ID-to-display-name mapping.
 */
fun ShadowrocketPolicyGroup.toEditableStrategyGroup(
    trafficConfigId: Int,
    serverChoices: List<ProxyGroupServerChoice>,
): StrategyGroup {
    val memberIds = members.mapNotNull { memberName ->
        serverChoices.firstOrNull { choice -> choice.rawName.equals(memberName, ignoreCase = true) }?.id
    }
    return StrategyGroup(
        remarks = name,
        strategy = type.toStrategyGroupTypeFromShadowrocketPolicy(),
        proxyServerIds = memberIds,
        displayMode = displayMode,
        sourceTrafficConfigId = trafficConfigId,
        sourcePolicyGroupName = name,
        probeInterval = intervalSeconds?.let { "${it}s" } ?: "1m",
        probeTimeout = timeoutSeconds?.let { "${it}s" } ?: "5s",
        probeUrl = url,
        enableBurstProbe = enableBurstProbe,
        tolerance = tolerance,
    )
}
