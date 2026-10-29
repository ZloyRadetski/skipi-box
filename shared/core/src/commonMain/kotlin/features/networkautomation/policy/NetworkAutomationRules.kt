// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.policy

import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType

/** Result of validating the rule editor's draft. */
data class NetworkRuleDraftValidation(
    val ssidDuplicate: Boolean,
    val typeDuplicate: Boolean,
    val canSave: Boolean,
)

/**
 * Rule list operations and editor rules shared by every host. List order is the rule priority:
 * the first enabled match wins, so edits keep a rule's position.
 */
object NetworkAutomationRules {
    fun upsert(rules: List<NetworkAutomationRule>, rule: NetworkAutomationRule): List<NetworkAutomationRule> {
        val index = rules.indexOfFirst { it.id == rule.id }
        return if (index >= 0) rules.toMutableList().apply { set(index, rule) } else rules + rule
    }

    fun remove(rules: List<NetworkAutomationRule>, id: String): List<NetworkAutomationRule> =
        rules.filter { it.id != id }

    fun setEnabled(rules: List<NetworkAutomationRule>, id: String, enabled: Boolean): List<NetworkAutomationRule> =
        rules.map { if (it.id == id) it.copy(enabled = enabled) else it }

    fun requiresServer(action: NetworkRuleAction): Boolean =
        action == NetworkRuleAction.SWITCH_SERVER || action == NetworkRuleAction.SWITCH_IF_CONNECTED

    fun validateDraft(
        type: NetworkRuleType,
        ssid: String?,
        action: NetworkRuleAction,
        editingRuleId: String?,
        existingRules: List<NetworkAutomationRule>,
        hasServers: Boolean,
    ): NetworkRuleDraftValidation {
        val others = existingRules.filter { it.id != editingRuleId }
        val ssidDuplicate = type == NetworkRuleType.SPECIFIC_WIFI && others.any { other ->
            other.type == NetworkRuleType.SPECIFIC_WIFI &&
                other.ssid?.trim()?.equals(ssid.orEmpty().trim(), ignoreCase = true) == true
        }
        val typeDuplicate = (type == NetworkRuleType.CELLULAR || type == NetworkRuleType.ANY_WIFI) &&
            others.any { it.type == type }
        val canSave = when {
            type == NetworkRuleType.SPECIFIC_WIFI && (ssid.isNullOrBlank() || ssidDuplicate) -> false
            typeDuplicate -> false
            requiresServer(action) && !hasServers -> false
            else -> true
        }
        return NetworkRuleDraftValidation(ssidDuplicate, typeDuplicate, canSave)
    }

    /** Builds the saved rule from the editor draft, keeping an edited rule's identity and switch. */
    fun buildRule(
        editing: NetworkAutomationRule?,
        type: NetworkRuleType,
        ssid: String?,
        action: NetworkRuleAction,
        targetServerId: Int?,
    ): NetworkAutomationRule = (editing ?: NetworkAutomationRule(type = type)).copy(
        type = type,
        ssid = if (type == NetworkRuleType.SPECIFIC_WIFI) ssid.orEmpty().trim() else null,
        action = action,
        targetServerId = targetServerId.takeIf { requiresServer(action) },
    )
}
