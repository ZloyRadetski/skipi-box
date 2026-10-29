// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.AppState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProxyServerState
import app.R
import app.SubscriptionGroupState
import app.collectAppState
import app.navigation.RouteOutboundSelectionResult
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.routing.RouteOutboundPickerLabels
import app.skipi.app.routing.RouteOutboundPickerProjection
import app.skipi.app.routing.RouteOutboundPickerSubscription
import app.skipi.app.routing.projectRouteOutboundPicker as projectSharedRouteOutboundPicker
import app.skipi.ui.routing.SkipiRouteOutboundSelectorScreen
import app.skipi.ui.routing.SkipiRoutingOutboundGroupDetails
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import app.skipi.ui.text.formatTemplate

/** Adapts Android state records to the shared route-outbound projection. */
internal fun projectRouteOutboundPicker(
    appState: AppState,
    selectedTag: String,
    trafficConfigId: Int?,
    labels: RouteOutboundPickerLabels,
): RouteOutboundPickerProjection = projectSharedRouteOutboundPicker(
    servers = appState.proxyServers.map(ProxyServerState::toProxyServerRecord),
    subscriptionGroups = appState.subscriptionGroups.map { group ->
        RouteOutboundPickerSubscription(
            id = group.id,
            name = group.name,
            builtIn = group.builtIn,
        )
    },
    trafficConfigs = appState.trafficConfigs,
    selectedTag = selectedTag,
    trafficConfigId = trafficConfigId,
    labels = labels,
)

private fun ProxyServerState.toProxyServerRecord() = ProxyServerRecord(
    id = this.id,
    groupId = this.groupId,
    server = this.server,
    latency = this.latency,
)

/** Full-screen selector with Android state, navigation, localization, and painter adapters. */
@Composable
fun RouteOutboundSelectorPage(
    padding: PaddingValues,
    selectedTag: String,
    resultKey: String,
    trafficConfigId: Int? = null,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val shadowrocketPolicyMode = trafficConfigId != null
    val labels = RouteOutboundPickerLabels(
        defaultGroupName = stringResource(R.string.subscription_default_group),
        autoBalancerName = stringResource(R.string.proxy_server_list_auto_balancers),
        systemTargetsName = stringResource(R.string.routing_system_targets),
        proxyLabel = stringResource(R.string.routing_outbound_proxy),
        directLabel = stringResource(R.string.routing_outbound_direct),
        blockLabel = stringResource(R.string.routing_outbound_block),
        proxyGroupsName = stringResource(R.string.configs_proxy_groups_title),
        configTargetsName = stringResource(R.string.nav_configs),
    )
    val projection = remember(
        appState.proxyServers,
        appState.subscriptionGroups,
        appState.trafficConfigs,
        selectedTag,
        trafficConfigId,
        labels,
    ) {
        projectRouteOutboundPicker(
            appState = appState,
            selectedTag = selectedTag,
            trafficConfigId = trafficConfigId,
            labels = labels,
        )
    }
    val subscriptionDetailsByGroupId = appState.subscriptionGroups.associate { group ->
        group.id to group.toRouteOutboundGroupDetails()
    }

    SkipiRouteOutboundSelectorScreen(
        projection = projection,
        selectedTag = selectedTag,
        isPolicyMode = shadowrocketPolicyMode,
        padding = padding,
        isWideScreen = isWideScreen,
        subscriptionDetailsByGroupId = subscriptionDetailsByGroupId,
        fallbackPainter = painterResource(R.drawable.ic_globe),
        onBack = navigator::pop,
        onConfirm = { targetTag ->
            navigator.setResult(resultKey, RouteOutboundSelectionResult(targetTag))
        },
    )
}

@Composable
private fun SubscriptionGroupState.toRouteOutboundGroupDetails(): SkipiRoutingOutboundGroupDetails {
    val lastUpdated = lastUpdatedAtMillis.takeIf { it > 0L }?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }
    return SkipiRoutingOutboundGroupDetails(
        traffic = subscriptionTrafficSummary()?.let { value ->
            stringResource(R.string.subscription_provider_traffic).formatTemplate("value" to value)
        },
        expiry = subscriptionExpirySummary(),
        trafficProgress = subscriptionTrafficProgress(),
        announcement = announce,
        updatedLabel = lastUpdated?.let { value ->
            stringResource(R.string.subscription_provider_updated).formatTemplate("value" to value)
        },
    )
}
