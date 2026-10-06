// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.proxy.server.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProxyServerState
import app.R
import app.SubscriptionGroupState
import app.collectAppState
import app.navigation.StrategyGroupMemberSelectionResult
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import app.skipi.ui.server.editor.SkipiStrategyGroupMemberSelectorScreen
import app.skipi.ui.server.editor.StrategyMemberSelectorGroup as SharedStrategyMemberSelectorGroup
import app.skipi.ui.server.editor.StrategyMemberSelectorItem
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.display.displayName
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.getTransportDisplay
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.text.formatTemplate

private data class StrategyMemberSelectorGroup(
    val key: String,
    val title: String,
    val subscriptionGroup: SubscriptionGroupState? = null,
    val servers: List<ProxyServerState>,
)

/** Explicit strategy-group membership includes auto-balancers; planners expand them later. */
@Composable
fun StrategyGroupMemberSelectorPage(
    padding: PaddingValues,
    selectedServerIds: List<Int>,
    excludedServerId: Int?,
    resultKey: String,
    requireServerRemarks: Boolean = false,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val autoBalancersName = stringResource(R.string.proxy_server_list_auto_balancers)
    val defaultProxyServerTemplate = stringResource(R.string.routing_default_proxy_server)
    val trafficSummaryTemplate = stringResource(R.string.subscription_provider_traffic)
    val lastUpdatedTemplate = stringResource(R.string.subscription_provider_updated)
    val selectableServers = remember(appState.proxyServers, excludedServerId, requireServerRemarks) {
        appState.proxyServers.filter { server ->
            server.id != excludedServerId &&
                server.server !is ChainProxy &&
                (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()) &&
                (!requireServerRemarks || server.server.getInfo().remarks.isNotBlank()) &&
                (server.server !is StrategyGroup || server.server.sourceTrafficConfigId == null)
        }
    }
    val groups = remember(appState.subscriptionGroups, selectableServers, defaultGroupName, autoBalancersName) {
        val serversByGroup = selectableServers.groupBy(ProxyServerState::groupId)
        buildList {
            val autoBalancerServers = serversByGroup[AutoBalancerGroupId].orEmpty().filter { it.server is StrategyGroup }
            if (autoBalancerServers.isNotEmpty()) add(StrategyMemberSelectorGroup("auto-balancers", autoBalancersName, servers = autoBalancerServers))
            appState.subscriptionGroups.filter { it.id != DefaultSubscriptionGroupId }.forEach { group ->
                add(StrategyMemberSelectorGroup("subscription-${group.id}", group.profileTitle.ifBlank { group.displayName(defaultGroupName) }, group, serversByGroup[group.id].orEmpty()))
            }
            val manualServers = serversByGroup[DefaultSubscriptionGroupId].orEmpty()
            if (manualServers.isNotEmpty() || appState.subscriptionGroups.none { it.id != DefaultSubscriptionGroupId }) {
                add(StrategyMemberSelectorGroup("manual-servers", defaultGroupName, appState.subscriptionGroups.firstOrNull { it.id == DefaultSubscriptionGroupId }, manualServers))
            }
        }
    }
    val subscriptionSummaries = groups.associate { group ->
        val sub = group.subscriptionGroup
        group.key to sub?.let {
            ServerPickerSubscriptionSummary(
                traffic = it.subscriptionTrafficSummary()?.let { traffic -> trafficSummaryTemplate.formatTemplate("value" to traffic) },
                expiry = it.subscriptionExpirySummary(),
                trafficProgress = it.subscriptionTrafficProgress(),
                announce = it.announce,
                lastUpdated = it.lastUpdatedAtMillis.takeIf { millis -> millis > 0L }?.let { millis ->
                    val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
                    lastUpdatedTemplate.formatTemplate("value" to formatted)
                },
            )
        }
    }
    val sharedGroups = remember(groups, defaultProxyServerTemplate, subscriptionSummaries) {
        groups.map { group ->
            SharedStrategyMemberSelectorGroup(
                key = group.key,
                title = group.title,
                summary = subscriptionSummaries[group.key],
                items = group.servers.map { server ->
                    val remarks = server.server.getInfo().remarks
                    val flag = CountryFlagUtils.extractLeadingCountryFlag(remarks)
                    val displayTitle = if (flag != null) CountryFlagUtils.stripLeadingCountryFlag(remarks)
                    else remarks.ifBlank { defaultProxyServerTemplate.replace("{id}", server.id.toString()) }
                    val transport = server.server.getTransportDisplay()
                    val protocol = server.server.getInfo().protocol
                    StrategyMemberSelectorItem(server.id, displayTitle, flag, if (!transport.isNullOrBlank()) "$protocol • $transport" else protocol)
                },
            )
        }
    }

    SkipiStrategyGroupMemberSelectorScreen(
        padding = padding,
        groups = sharedGroups,
        selectedServerIds = selectedServerIds,
        title = stringResource(R.string.proxy_editor_strategy_group_select_servers),
        isWideScreen = isWideScreen,
        fallbackPainter = painterResource(R.drawable.ic_globe),
        flagContainerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        flagFallbackTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
        selectedSummary = { count -> stringResource(R.string.proxy_editor_strategy_group_selected_servers_summary, count) },
        emptyGroupMessage = stringResource(R.string.proxy_editor_strategy_group_no_servers),
        saveLabel = stringResource(R.string.common_save),
        onBack = navigator::pop,
        onSave = { ids -> navigator.setResult(resultKey, StrategyGroupMemberSelectionResult(ids)) },
    )
}
