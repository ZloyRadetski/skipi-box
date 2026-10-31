// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.proxy.ProxyServerRecord as RouteProxyServerRecord
import app.skipi.app.routing.RouteOutboundPickerLabels
import app.skipi.app.routing.RouteOutboundPickerSubscription
import app.skipi.app.routing.projectRouteOutboundPicker
import app.skipi.ui.config.SkipiTrafficConfigRuleEditorPage
import app.skipi.ui.config.SkipiTrafficConfigRulesEditorScreen
import app.skipi.ui.config.rememberSkipiTrafficConfigRuleEditorState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_proxy_groups_title
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.nav_configs
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
    statusContent: (@Composable () -> Unit)? = null,
) {
    var editorSession by remember(profile.id) { mutableStateOf<DesktopRuleEditorSession?>(null) }
    var nextEditorSessionId by remember(profile.id) { mutableIntStateOf(0) }
    var showPolicySelector by remember(profile.id) { mutableStateOf(false) }

    val rules = remember(profile.id, profile.rawConfig) {
        profile.rawConfig.analyzeShadowrocketConfig().rules
    }
    val session = editorSession
    val initialRule = session?.lineNumber?.let { requestedLine ->
        rules.firstOrNull { it.lineNumber == requestedLine }
    }
    val editRuleMissing = session?.lineNumber != null && initialRule == null

    androidx.compose.runtime.LaunchedEffect(session?.instanceId, profile.id, profile.rawConfig) {
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

    if (session == null || editRuleMissing) {
        SkipiTrafficConfigRulesEditorScreen(
            title = stringResource(Res.string.configs_rules_title),
            saveLabel = stringResource(Res.string.common_save),
            padding = padding,
            isWideScreen = isWideScreen,
            profile = profile,
            controller = controller,
            onBack = onBack,
            onSave = onBack,
            onAdd = { openRuleEditor(lineNumber = null) },
            onEdit = { rule -> openRuleEditor(lineNumber = rule.lineNumber) },
            onHapticFeedback = {},
        )
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
            val labels = RouteOutboundPickerLabels(
                defaultGroupName = stringResource(Res.string.subscription_default_group),
                autoBalancerName = stringResource(Res.string.proxy_server_list_auto_balancers),
                systemTargetsName = stringResource(Res.string.routing_system_targets),
                proxyLabel = stringResource(Res.string.routing_outbound_proxy),
                directLabel = stringResource(Res.string.routing_outbound_direct),
                blockLabel = stringResource(Res.string.routing_outbound_block),
                proxyGroupsName = stringResource(Res.string.configs_proxy_groups_title),
                configTargetsName = stringResource(Res.string.nav_configs),
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
                editorState.selectedPolicy,
                profile.id,
                labels,
            ) {
                projectRouteOutboundPicker(
                    servers = routeServers,
                    subscriptionGroups = routeSubscriptions,
                    trafficConfigs = trafficConfigs,
                    selectedTag = editorState.selectedPolicy,
                    trafficConfigId = profile.id,
                    labels = labels,
                    defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                )
            }
            val subscriptionDetails = subscriptionCatalog.subscriptions.associate { subscription ->
                val group = subscription.toSubscriptionGroupUiState(labels.defaultGroupName)
                group.id to group.toOutboundGroupDetails()
            }

            if (showPolicySelector) {
                key(selectorResultKey) {
                    SkipiRouteOutboundSelectorScreen(
                        projection = routeProjection,
                        selectedTag = editorState.selectedPolicy,
                        isPolicyMode = true,
                        padding = padding,
                        isWideScreen = isWideScreen,
                        subscriptionDetailsByGroupId = subscriptionDetails,
                        onBack = { showPolicySelector = false },
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
