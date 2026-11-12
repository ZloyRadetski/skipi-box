// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.proxy.ProxyServerRow as RouteProxyServerRecord
import app.skipi.app.routing.RouteOutboundPickerLabels
import app.skipi.app.routing.RouteOutboundPickerSubscription
import app.skipi.app.routing.projectRouteOutboundPicker
import app.skipi.ui.config.SkipiTrafficConfigRuleEditorPage
import app.skipi.ui.config.SkipiTrafficConfigRulesEditorScreen
import app.skipi.ui.config.rememberSkipiRoutingTableDraft
import app.skipi.ui.config.rememberSkipiRoutingTableState
import app.skipi.ui.config.rememberSkipiTrafficConfigRuleEditorState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.proxy_server_list_auto_balancers
import app.skipi.ui.resources.routing_outbound_block
import app.skipi.ui.resources.routing_outbound_direct
import app.skipi.ui.resources.routing_outbound_proxy
import app.skipi.ui.resources.routing_system_targets
import app.skipi.ui.resources.subscription_default_group
import app.skipi.ui.routing.SkipiRouteOutboundSelectorScreen
import app.skipi.ui.routing.SkipiRoutingOutboundGroupDetails
import app.skipi.ui.subscription.SubscriptionGroupUiState
import app.skipi.ui.subscription.toSubscriptionGroupUiState
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import org.jetbrains.compose.resources.stringResource

/** Narrower windows keep the cards: the table needs room for its columns and the editor beneath it. */
private val RoutingTableMinWidth = 900.dp

@Composable
internal fun DesktopTrafficConfigRulesEditor(
    profile: TrafficConfigState,
    trafficConfigs: List<TrafficConfigState>,
    controller: TrafficConfigEditorController,
    proxyServers: List<CatalogProxyServerRecord>,
    subscriptionCatalog: SubscriptionCatalog,
    resourceFiles: DesktopProfileResourceFiles,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    fetchRoutingDocument: suspend (url: String) -> String,
    statusContent: (@Composable () -> Unit)? = null,
) {
    var editorSession by remember(profile.id) { mutableStateOf<DesktopRuleEditorSession?>(null) }
    var nextEditorSessionId by remember(profile.id) { mutableIntStateOf(0) }
    var showPolicySelector by remember(profile.id) { mutableStateOf(false) }
    // The table keeps what it has open while the server picker takes its place.
    val tableState = rememberSkipiRoutingTableState(profile.id)
    // The table edits a working copy: every write of the profile restarts a running tunnel, so it is written on
    // Apply and on leaving, not after each change.
    val tableDraft = rememberSkipiRoutingTableDraft(profile, controller)
    DisposableEffect(tableDraft) {
        onDispose { tableDraft.apply(tableState) }
    }
    var tablePolicyPick by remember(profile.id) { mutableStateOf<String?>(null) }
    val importSources = remember(fetchRoutingDocument) { DesktopRoutingImportSources.system(fetchRoutingDocument) }

    val rules = remember(profile.id, profile.rawConfig) {
        profile.rawConfig.analyzeShadowrocketConfig().rules
    }
    val session = editorSession
    val initialRule = session?.lineNumber?.let { requestedLine ->
        rules.firstOrNull { it.lineNumber == requestedLine }
    }
    val editRuleMissing = session?.lineNumber != null && initialRule == null

    LaunchedEffect(session?.instanceId, profile.id, profile.rawConfig) {
        if (editRuleMissing) {
            editorSession = null
            showPolicySelector = false
        }
    }

    fun openRuleEditor(lineNumber: Int?) {
        nextEditorSessionId += 1
        editorSession = DesktopRuleEditorSession(
            lineNumber = lineNumber,
            instanceId = nextEditorSessionId,
        )
        showPolicySelector = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showTable = maxWidth >= RoutingTableMinWidth
        LaunchedEffect(showTable) {
            if (!showTable) tableDraft.apply(tableState)
        }
        if (session == null || editRuleMissing) {
            val pick = tablePolicyPick
            when {
                showTable && pick != null -> key(profile.id, "table-policy") {
                    DesktopPolicySelector(
                        profile = profile,
                        trafficConfigs = trafficConfigs,
                        proxyServers = proxyServers,
                        subscriptionCatalog = subscriptionCatalog,
                        latencyByServerId = latencyByServerId,
                        selectedPolicy = pick,
                        padding = padding,
                        isWideScreen = isWideScreen,
                        onConfirm = { targetTag ->
                            tableState.applyPolicy(targetTag)
                            tablePolicyPick = null
                        },
                    )
                }

                showTable -> DesktopRoutingTableScreen(
                    profile = tableDraft.profile,
                    controller = tableDraft.controller,
                    state = tableState,
                    sources = importSources,
                    padding = padding,
                    isWideScreen = isWideScreen,
                    onBack = onBack,
                    onPickPolicy = { current -> tablePolicyPick = current },
                    onEditRule = { rule ->
                        // The rule form works on the profile itself, so it has to see what the table holds.
                        tableDraft.apply(tableState)
                        openRuleEditor(lineNumber = rule.lineNumber)
                    },
                    canApply = tableDraft.hasChanges,
                    onApply = { tableDraft.apply(tableState) },
                )

                else -> SkipiTrafficConfigRulesEditorScreen(
                    title = stringResource(Res.string.configs_rules_title),
                    padding = padding,
                    isWideScreen = isWideScreen,
                    profile = profile,
                    controller = controller,
                    onBack = onBack,
                    onAdd = { openRuleEditor(lineNumber = null) },
                    onEdit = { rule -> openRuleEditor(lineNumber = rule.lineNumber) },
                    onHapticFeedback = {},
                )
            }
        }

        if (session != null && !editRuleMissing) {
            key(session.instanceId) {
                val editorIdentity = session.lineNumber ?: -profile.id
                val suggestionsProvider = remember(resourceFiles) {
                    DesktopRoutingSuggestionsProvider(resourceFiles)
                }
                val geoSiteSuggestions = remember(profile, suggestionsProvider) {
                    suggestionsProvider.resolveGeoSiteSuggestions(profile)
                }
                val geoIpSuggestions = remember(profile, suggestionsProvider) {
                    suggestionsProvider.resolveGeoIpSuggestions(profile, forShadowrocket = true)
                }
                val ruleSetSuggestions = remember(profile, suggestionsProvider) {
                    suggestionsProvider.resolveRuleSetSuggestions(profile)
                }
                val editorState = rememberSkipiTrafficConfigRuleEditorState(
                    editorIdentity = editorIdentity,
                    initialRule = initialRule,
                )
                val selectorResultKey = remember(profile.id, editorIdentity) {
                    "traffic-config-rule-policy-${profile.id}-$editorIdentity"
                }

                if (showPolicySelector) {
                    key(selectorResultKey) {
                        DesktopPolicySelector(
                            profile = profile,
                            trafficConfigs = trafficConfigs,
                            proxyServers = proxyServers,
                            subscriptionCatalog = subscriptionCatalog,
                            latencyByServerId = latencyByServerId,
                            selectedPolicy = editorState.selectedPolicy,
                            padding = padding,
                            isWideScreen = isWideScreen,
                            onConfirm = { targetTag ->
                                editorState.selectedPolicy = targetTag
                                showPolicySelector = false
                            },
                        )
                    }
                } else {
                    SkipiTrafficConfigRuleEditorPage(
                        padding = padding,
                        isWideScreen = isWideScreen,
                        profileId = profile.id,
                        editorIdentity = editorIdentity,
                        initialRule = initialRule,
                        editorState = editorState,
                        controller = controller,
                        geoSiteSuggestions = geoSiteSuggestions,
                        geoIpSuggestions = geoIpSuggestions,
                        ruleSetSuggestions = ruleSetSuggestions,
                        onBack = {
                            editorSession = null
                            showPolicySelector = false
                        },
                        onPolicyClick = { showPolicySelector = true },
                        statusContent = statusContent,
                    )
                }
            }
        }
    }
}

/** The shared outbound selector, fed with the servers, groups and profiles a rule can send traffic to. */
@Composable
private fun DesktopPolicySelector(
    profile: TrafficConfigState,
    trafficConfigs: List<TrafficConfigState>,
    proxyServers: List<CatalogProxyServerRecord>,
    subscriptionCatalog: SubscriptionCatalog,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    selectedPolicy: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onConfirm: (String) -> Unit,
) {
    val labels = RouteOutboundPickerLabels(
        defaultGroupName = stringResource(Res.string.subscription_default_group),
        autoBalancerName = stringResource(Res.string.proxy_server_list_auto_balancers),
        systemTargetsName = stringResource(Res.string.routing_system_targets),
        proxyLabel = stringResource(Res.string.routing_outbound_proxy),
        directLabel = stringResource(Res.string.routing_outbound_direct),
        blockLabel = stringResource(Res.string.routing_outbound_block),
    )
    val routeServers = remember(proxyServers, latencyByServerId) {
        proxyServers.map { server ->
            RouteProxyServerRecord(
                id = server.id,
                groupId = server.sourceSubscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                server = server.server,
                latency = (latencyByServerId[server.id] as? DesktopServerLatencyResult.Success)
                    ?.milliseconds
                    ?.toString()
                    .orEmpty(),
            )
        }
    }
    val routeSubscriptions = remember(subscriptionCatalog) {
        subscriptionCatalog.subscriptions.map { subscription ->
            RouteOutboundPickerSubscription(
                id = subscription.id,
                name = subscription.title,
                builtIn = subscription.builtIn,
            )
        }
    }
    val routeProjection = remember(
        routeServers,
        routeSubscriptions,
        trafficConfigs,
        selectedPolicy,
        profile.id,
        labels,
    ) {
        projectRouteOutboundPicker(
            servers = routeServers,
            subscriptionGroups = routeSubscriptions,
            trafficConfigs = trafficConfigs,
            selectedTag = selectedPolicy,
            trafficConfigId = profile.id,
            labels = labels,
            defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
        )
    }
    val subscriptionDetails = subscriptionCatalog.subscriptions.associate { subscription ->
        val group = subscription.toSubscriptionGroupUiState(labels.defaultGroupName)
        group.id to group.toOutboundGroupDetails()
    }

    SkipiRouteOutboundSelectorScreen(
        projection = routeProjection,
        selectedTag = selectedPolicy,
        isPolicyMode = true,
        padding = padding,
        isWideScreen = isWideScreen,
        subscriptionDetailsByGroupId = subscriptionDetails,
        onConfirm = onConfirm,
    )
}

private data class DesktopRuleEditorSession(
    val lineNumber: Int?,
    val instanceId: Int,
)

@Composable
private fun SubscriptionGroupUiState.toOutboundGroupDetails(): SkipiRoutingOutboundGroupDetails {
    val summary = toServerPickerSubscriptionSummary()
    return SkipiRoutingOutboundGroupDetails(
        traffic = summary.traffic,
        expiry = summary.expiry,
        trafficProgress = summary.trafficProgress,
        announcement = summary.announce.orEmpty(),
        updatedLabel = summary.lastUpdated,
    )
}
