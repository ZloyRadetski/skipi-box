// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.DefaultRouteOutboundTag
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProxyServerState
import app.R
import app.collectAppState
import app.navigation.RouteOutboundSelectionResult
import app.proxyServerOutboundTag
import features.config.analyzeShadowrocketConfig
import features.proxy.server.display.displayName
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import app.SubscriptionGroupState
import app.skipi.ui.routing.SkipiRoutingPageScaffold
import app.skipi.ui.routing.SkipiRoutingOutboundGroup
import app.skipi.ui.routing.SkipiRoutingOutboundGroupDetails
import app.skipi.ui.routing.SkipiRoutingOutboundOption
import app.skipi.ui.routing.SkipiRoutingOutboundGroupHeader
import app.skipi.ui.routing.SkipiRoutingOutboundOptionRow
import app.skipi.ui.routing.SkipiRoutingOutboundEmptyGroupRow
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.getTransportDisplay
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import ui.text.formatTemplate
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.components.NavigationIcon
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import androidx.compose.ui.graphics.Color
import java.text.DateFormat
import java.util.Date

private sealed interface RouteOutboundItem {
    val key: String

    data class Static(
        val tag: String,
        val title: String,
        val summary: String = "",
    ) : RouteOutboundItem {
        override val key: String get() = "static-$tag"
    }

    data class Server(
        val serverState: ProxyServerState,
        val shadowrocketPolicyMode: Boolean,
    ) : RouteOutboundItem {
        override val key: String get() = "server-${serverState.id}"

        fun resolveTag(): String {
            return if (shadowrocketPolicyMode) {
                serverState.server.getInfo().remarks.trim()
            } else {
                serverState.proxyServerOutboundTag()
            }
        }
    }
}

private data class RouteOutboundPickerGroup(
    val key: String,
    val title: String,
    val subscriptionGroup: SubscriptionGroupState? = null,
    val items: List<RouteOutboundItem>,
)

/** Full-screen, grouped target picker with lazy evaluations and instant opening. */
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
    val listState = rememberLazyListState()
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val autoBalancerName = stringResource(R.string.proxy_server_list_auto_balancers)
    val defaultProxyServerTemplate = stringResource(R.string.routing_default_proxy_server)
    val systemTargetsName = stringResource(R.string.routing_system_targets)
    val proxyLabel = stringResource(R.string.routing_outbound_proxy)
    val directLabel = stringResource(R.string.routing_outbound_direct)
    val blockLabel = stringResource(R.string.routing_outbound_block)
    val proxyGroupsName = stringResource(R.string.configs_proxy_groups_title)
    val configTargetsName = stringResource(R.string.nav_configs)
    val shadowrocketPolicyMode = trafficConfigId != null

    val trafficConfig = remember(appState.trafficConfigs, trafficConfigId) {
        trafficConfigId?.let { id -> appState.trafficConfigs.firstOrNull { it.id == id } }
    }
    val shadowrocketGroups = remember(trafficConfig?.rawConfig) {
        trafficConfig?.rawConfig?.analyzeShadowrocketConfig()?.proxyGroups.orEmpty()
    }
    var targetTag by remember(selectedTag) { mutableStateOf(selectedTag) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val selectableServers = remember(appState.proxyServers, shadowrocketPolicyMode) {
        appState.proxyServers.filter { server ->
            (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()) &&
                (!shadowrocketPolicyMode || server.server.getInfo().remarks.isNotBlank())
        }
    }

    val serversByGroup = remember(selectableServers) {
        selectableServers.groupBy(ProxyServerState::groupId)
    }

    val groups = remember(
        selectableServers,
        serversByGroup,
        appState.subscriptionGroups,
        appState.trafficConfigs,
        shadowrocketGroups,
        defaultGroupName,
        autoBalancerName,
        systemTargetsName,
        proxyLabel,
        directLabel,
        blockLabel,
        proxyGroupsName,
        configTargetsName,
        selectedTag,
        shadowrocketPolicyMode,
        trafficConfigId,
    ) {
        val systemEntries = mutableListOf(
            RouteOutboundItem.Static(
                tag = if (shadowrocketPolicyMode) "PROXY" else DefaultRouteOutboundTag,
                title = proxyLabel,
            ),
            RouteOutboundItem.Static(
                tag = if (shadowrocketPolicyMode) "DIRECT" else "direct",
                title = directLabel,
            ),
            RouteOutboundItem.Static(
                tag = if (shadowrocketPolicyMode) "REJECT" else "block",
                title = blockLabel,
            ),
        )

        val autoBalancerItems = serversByGroup[AutoBalancerGroupId].orEmpty()
            .filter { server -> server.server is StrategyGroup && server.server.sourceTrafficConfigId == null }
            .map { RouteOutboundItem.Server(it, shadowrocketPolicyMode) }

        val subscriptionGroupItems = appState.subscriptionGroups
            .filter { it.id != DefaultSubscriptionGroupId }
            .map { group ->
                RouteOutboundPickerGroup(
                    key = "routing-subscription-${group.id}",
                    title = group.displayName(defaultGroupName),
                    subscriptionGroup = group,
                    items = serversByGroup[group.id].orEmpty().map {
                        RouteOutboundItem.Server(it, shadowrocketPolicyMode)
                    },
                )
            }

        val manualGroupItems = serversByGroup[DefaultSubscriptionGroupId].orEmpty().map {
            RouteOutboundItem.Server(it, shadowrocketPolicyMode)
        }

        val configProxyGroups = shadowrocketGroups.map { group ->
            RouteOutboundItem.Static(
                tag = group.name,
                title = group.name,
                summary = group.type,
            )
        }

        val otherConfigs = if (shadowrocketPolicyMode) {
            appState.trafficConfigs
                .filter { it.id != trafficConfigId }
                .map { config ->
                    RouteOutboundItem.Static(
                        tag = "CONFIG:${config.id}",
                        title = config.name,
                        summary = "CONFIG:${config.id}",
                    )
                }
        } else {
            emptyList()
        }

        if (selectedTag.isNotBlank()) {
            val isKnown = systemEntries.any { it.tag.equals(selectedTag, ignoreCase = true) } ||
                configProxyGroups.any { it.tag == selectedTag } ||
                otherConfigs.any { it.tag == selectedTag } ||
                selectableServers.any { server ->
                    if (shadowrocketPolicyMode) {
                        server.server.getInfo().remarks.trim() == selectedTag
                    } else {
                        server.proxyServerOutboundTag() == selectedTag
                    }
                }
            if (!isKnown) {
                systemEntries += RouteOutboundItem.Static(
                    tag = selectedTag,
                    title = selectedTag,
                )
            }
        }

        buildList {
            add(
                RouteOutboundPickerGroup(
                    key = "routing-system-targets",
                    title = systemTargetsName,
                    subscriptionGroup = null,
                    items = systemEntries,
                ),
            )
            add(
                RouteOutboundPickerGroup(
                    key = "routing-auto-balancers",
                    title = autoBalancerName,
                    subscriptionGroup = null,
                    items = autoBalancerItems,
                ),
            )
            addAll(subscriptionGroupItems)
            add(
                RouteOutboundPickerGroup(
                    key = "routing-manual-servers",
                    title = defaultGroupName,
                    subscriptionGroup = appState.subscriptionGroups.firstOrNull { it.id == DefaultSubscriptionGroupId },
                    items = manualGroupItems,
                ),
            )
            if (shadowrocketPolicyMode) {
                add(
                    RouteOutboundPickerGroup(
                        key = "routing-config-proxy-groups",
                        title = proxyGroupsName,
                        subscriptionGroup = null,
                        items = configProxyGroups,
                    ),
                )
                add(
                    RouteOutboundPickerGroup(
                        key = "routing-config-targets",
                        title = configTargetsName,
                        subscriptionGroup = null,
                        items = otherConfigs,
                    ),
                )
            }
        }
    }

    val initiallyExpandedGroupKey = remember(selectedTag, groups) {
        val matchingGroup = groups.firstOrNull { group ->
            if (
                selectedTag.isBlank() || selectedTag == "PROXY" || selectedTag == DefaultRouteOutboundTag ||
                selectedTag.equals("DIRECT", ignoreCase = true) || selectedTag.equals("REJECT", ignoreCase = true) ||
                selectedTag.equals("block", ignoreCase = true)
            ) {
                group.key == "routing-system-targets"
            } else {
                group.items.any { item ->
                    when (item) {
                        is RouteOutboundItem.Static -> item.tag == selectedTag
                        is RouteOutboundItem.Server -> item.resolveTag() == selectedTag
                    }
                }
            }
        }
        matchingGroup?.key ?: groups.firstOrNull()?.key
    }

    val presentationGroups = groups.map { group ->
        val subscription = group.subscriptionGroup
        val lastUpdated = subscription?.lastUpdatedAtMillis?.takeIf { it > 0L }?.let { millis ->
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
        }
        val details = subscription?.let {
            SkipiRoutingOutboundGroupDetails(
                traffic = it.subscriptionTrafficSummary()?.let { value ->
                    stringResource(R.string.subscription_provider_traffic).formatTemplate("value" to value)
                },
                expiry = it.subscriptionExpirySummary(),
                trafficProgress = it.subscriptionTrafficProgress(),
                announcement = it.announce,
                updatedLabel = lastUpdated?.let { value ->
                    stringResource(R.string.subscription_provider_updated).formatTemplate("value" to value)
                },
            )
        }
        SkipiRoutingOutboundGroup(
            key = group.key,
            title = group.title,
            countLabel = stringResource(R.string.subscription_provider_servers, group.items.size),
            details = details,
            items = group.items.map { item ->
                when (item) {
                    is RouteOutboundItem.Static -> {
                        val flag = when (item.tag) {
                            "PROXY", DefaultRouteOutboundTag -> "⚡"
                            "DIRECT", "direct" -> "🔄"
                            "REJECT", "block" -> "🚫"
                            else -> if (item.tag.startsWith("CONFIG:")) "⚙️" else "🌐"
                        }
                        SkipiRoutingOutboundOption(
                            key = item.key,
                            tag = item.tag,
                            title = item.title,
                            subtitle = item.summary.takeIf { it.isNotBlank() },
                            flag = flag,
                        )
                    }
                    is RouteOutboundItem.Server -> {
                        val info = item.serverState.server.getInfo()
                        val rawRemarks = info.remarks
                        val flag = CountryFlagUtils.extractLeadingCountryFlag(rawRemarks)
                        val title = if (flag != null) {
                            CountryFlagUtils.stripLeadingCountryFlag(rawRemarks)
                        } else {
                            rawRemarks.ifBlank { defaultProxyServerTemplate.replace("{id}", item.serverState.id.toString()) }
                        }
                        val transport = item.serverState.server.getTransportDisplay()
                        val subtitle = if (!transport.isNullOrBlank()) "${info.protocol} • $transport" else info.protocol
                        SkipiRoutingOutboundOption(
                            key = item.key,
                            tag = item.resolveTag(),
                            title = title,
                            subtitle = subtitle,
                            flag = flag,
                        )
                    }
                }
            },
        )
    }

    SkipiRoutingPageScaffold(
        title = stringResource(if (shadowrocketPolicyMode) R.string.configs_rules_policy else R.string.routing_outbound_tag_label),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        actions = {
            NavigationIcon(
                onClick = { navigator.setResult(resultKey, RouteOutboundSelectionResult(targetTag)) },
                imageVector = MiuixIcons.Ok,
                contentDescription = stringResource(R.string.common_save),
            )
        },
    ) { contentPadding, scrollBehavior ->
        val globePainter = painterResource(app.R.drawable.ic_globe)
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                presentationGroups.forEach { group ->
                    val expanded = expandedGroups[group.key] ?: (group.key == initiallyExpandedGroupKey)
                    item(key = "route-outbound-group-${group.key}") {
                        SkipiRoutingOutboundGroupHeader(
                            title = group.title,
                            countLabel = group.countLabel,
                            itemCount = group.items.size,
                            details = group.details,
                            expanded = expanded,
                            onExpandedChange = { expandedGroups[group.key] = it },
                        )
                    }
                    if (expanded) {
                        if (group.items.isEmpty()) {
                            item(key = "route-outbound-empty-${group.key}") {
                                SkipiRoutingOutboundEmptyGroupRow(
                                    message = stringResource(R.string.proxy_editor_strategy_group_no_servers),
                                )
                            }
                        } else {
                            items(
                                items = group.items,
                                key = { item -> "route-outbound-entry-${group.key}-${item.key}" },
                                contentType = { "route-outbound-entry" },
                            ) { item ->
                                SkipiRoutingOutboundOptionRow(
                                    flag = item.flag,
                                    fallbackPainter = globePainter,
                                    displayTitle = item.title,
                                    subtitle = item.subtitle,
                                    selected = item.tag == targetTag,
                                    isLast = item.key == group.items.lastOrNull()?.key,
                                    onClick = { targetTag = item.tag },
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
