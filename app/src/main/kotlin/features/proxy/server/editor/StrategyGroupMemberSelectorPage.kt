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
import app.AppState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProxyServerState
import app.R
import app.SubscriptionGroupState
import app.collectAppState
import app.navigation.StrategyGroupMemberSelectionResult
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.StrategyMemberSelectorSubscription
import app.skipi.app.proxy.projectStrategyGroupMemberGroups as projectSharedStrategyGroupMemberGroups
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import app.skipi.ui.server.editor.SkipiStrategyGroupMemberSelectorScreen
import app.skipi.ui.server.editor.StrategyMemberSelectorGroup as SharedStrategyMemberSelectorGroup
import app.skipi.ui.server.editor.StrategyMemberSelectorItem
import app.skipi.ui.server.display.CountryFlagUtils
import features.proxy.server.model.getTransportDisplay
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.skipi.ui.text.formatTemplate

internal data class StrategyMemberSelectorGroup(
    val key: String,
    val title: String,
    val subscriptionGroup: SubscriptionGroupState? = null,
    val servers: List<ProxyServerState>,
)

/** Keeps the Android member-picker catalog policy independently testable. */
internal fun projectStrategyGroupMemberGroups(
    appState: AppState,
    excludedServerId: Int?,
    requireServerRemarks: Boolean,
    defaultGroupName: String,
    autoBalancerName: String,
): List<StrategyMemberSelectorGroup> {
    val subscriptionInputs = appState.subscriptionGroups.map { group ->
        StrategyMemberSelectorSubscription(
            id = group.id,
            name = group.name,
            profileTitle = group.profileTitle,
        )
    }
    val projection = projectSharedStrategyGroupMemberGroups(
        servers = appState.proxyServers.map { server ->
            ProxyServerRecord(
                id = server.id,
                groupId = server.groupId,
                server = server.server,
                latency = server.latency,
            )
        },
        subscriptionGroups = subscriptionInputs,
        defaultGroupName = defaultGroupName,
        autoBalancerName = autoBalancerName,
        excludedServerId = excludedServerId,
        requireServerRemarks = requireServerRemarks,
    )
    return projection.map { group ->
        StrategyMemberSelectorGroup(
            key = group.key,
            title = group.title,
            subscriptionGroup = group.subscriptionGroupId?.let { id ->
                appState.subscriptionGroups.firstOrNull { it.id == id }
            },
            servers = group.servers.map { server ->
                ProxyServerState(
                    id = server.id,
                    server = server.server,
                    groupId = server.groupId,
                    latency = server.latency,
                )
            },
        )
    }
}

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
    val groups = remember(
        appState.proxyServers,
        appState.subscriptionGroups,
        excludedServerId,
        requireServerRemarks,
        defaultGroupName,
        autoBalancersName,
    ) {
        projectStrategyGroupMemberGroups(
            appState = appState,
            excludedServerId = excludedServerId,
            requireServerRemarks = requireServerRemarks,
            defaultGroupName = defaultGroupName,
            autoBalancerName = autoBalancersName,
        )
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
