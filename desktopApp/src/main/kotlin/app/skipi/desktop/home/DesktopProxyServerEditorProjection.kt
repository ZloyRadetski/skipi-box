// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.StrategyMemberSelectorSubscription
import app.skipi.app.proxy.projectStrategyGroupMemberGroups
import app.skipi.ui.server.editor.ProxyServerEditorGroupOption
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import app.skipi.ui.server.editor.StrategyMemberSelectorGroup
import app.skipi.ui.server.editor.StrategyMemberSelectorItem
import app.skipi.ui.server.editor.projectProxyServerEditorGroupOptions
import app.skipi.ui.subscription.SubscriptionGroupUiState
import app.skipi.ui.subscription.toSubscriptionGroupUiState
import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.model.stripLeadingCountryFlag

/** Source-aware inputs shared by Desktop's library and traffic-profile server editors. */
internal data class DesktopProxyServerEditorInputs(
    val groupOptions: List<ProxyServerEditorGroupOption>,
    val memberGroups: List<StrategyMemberSelectorGroup>,
)

internal fun projectDesktopProxyServerEditorInputs(
    proxyServers: List<ProxyServerRecord>,
    subscriptionCatalog: SubscriptionCatalog,
    subscriptionSummaries: Map<Int, ServerPickerSubscriptionSummary>,
    allGroupsLabel: String,
    defaultGroupName: String,
    autoBalancerName: String,
    defaultProxyServerTemplate: String,
    excludedServerId: Int?,
): DesktopProxyServerEditorInputs {
    val subscriptionGroupsFromCatalog = subscriptionCatalog.subscriptions.map { subscription ->
        subscription.toSubscriptionGroupUiState(defaultGroupName)
    }
    val subscriptionGroups = if (
        subscriptionGroupsFromCatalog.any { it.id == DesktopProxyGroupIds.DefaultManualSubscriptionId }
    ) {
        subscriptionGroupsFromCatalog
    } else {
        subscriptionGroupsFromCatalog + SubscriptionGroupUiState(
            id = DesktopProxyGroupIds.DefaultManualSubscriptionId,
            name = defaultGroupName,
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
    }
    val groupOptions = projectProxyServerEditorGroupOptions(
        groups = subscriptionGroups,
        allGroupsLabel = allGroupsLabel,
        defaultGroupName = defaultGroupName,
        defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
    )
    val memberGroupProjection = projectStrategyGroupMemberGroups(
        servers = proxyServers,
        subscriptionGroups = subscriptionCatalog.subscriptions.map { subscription ->
            StrategyMemberSelectorSubscription(
                id = subscription.id,
                name = subscription.title,
                profileTitle = subscription.metadata?.profileTitle.orEmpty(),
            )
        },
        defaultGroupName = defaultGroupName,
        autoBalancerName = autoBalancerName,
        excludedServerId = excludedServerId,
        defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
    )
    val memberGroups = memberGroupProjection.map { group ->
        StrategyMemberSelectorGroup(
            key = group.key,
            title = group.title,
            summary = group.subscriptionGroupId?.let(subscriptionSummaries::get),
            items = group.servers.map { server ->
                val remarks = server.server.getInfo().remarks
                val flag = remarks.extractLeadingCountryFlagOrNull()
                val name = remarks.stripLeadingCountryFlag()
                    .ifBlank { defaultProxyServerTemplate.replace("{id}", server.id.toString()) }
                val transport = server.server.getTransportDisplay()
                val protocol = server.server.getInfo().protocol
                StrategyMemberSelectorItem(
                    id = server.id,
                    displayTitle = name,
                    flag = flag,
                    subtitle = if (!transport.isNullOrBlank()) "$protocol • $transport" else protocol,
                )
            },
        )
    }
    return DesktopProxyServerEditorInputs(
        groupOptions = groupOptions,
        memberGroups = memberGroups,
    )
}
