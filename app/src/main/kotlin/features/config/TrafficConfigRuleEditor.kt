// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import app.AppState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.navigation.RouteOutboundSelectionResult
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.ui.config.SkipiTrafficConfigRuleEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigRulesEditorScreen
import app.skipi.ui.config.TrafficConfigRuleTypes
import app.skipi.ui.config.rememberSkipiTrafficConfigRuleEditorState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import features.routing.ui.GeoAssetPickerDialog
import features.routing.ui.RoutingRulesInfoBottomSheet
import features.routing.usecase.RoutingSuggestionsProvider
import features.settings.SettingsIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Full-screen visual editor for the [Rule] section. Rules are evaluated from top to bottom. */
@Composable
internal fun TrafficConfigRulesPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run {
        navigator.pop()
        return
    }
    val editor = rememberTrafficConfigRuleEditorController(updateAppState)
    var showInfoBottomSheet by rememberSaveable { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    SkipiTrafficConfigRulesEditorScreen(
        title = stringResource(R.string.configs_rules_title),
        saveLabel = stringResource(R.string.common_save),
        padding = padding,
        isWideScreen = isWideScreen,
        profile = config,
        controller = editor,
        onBack = navigator::pop,
        onSave = navigator::pop,
        onAdd = { navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId)) },
        onEdit = { rule -> navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId, rule.lineNumber)) },
        onHapticFeedback = hapticFeedback::performHapticFeedback,
        platformBackHandler = { onBack -> TrafficConfigRulesBackHandler(onBack) },
        actions = {
            IconButton(onClick = { showInfoBottomSheet = true }) {
                Icon(
                    imageVector = SettingsIcons.Info,
                    contentDescription = stringResource(R.string.routing_info_title),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    )

    RoutingRulesInfoBottomSheet(
        show = showInfoBottomSheet,
        onDismissRequest = { showInfoBottomSheet = false },
    )
}

@Composable
fun TrafficConfigRuleEditorPage(
    padding: PaddingValues,
    trafficConfigId: Int,
    ruleLineNumber: Int?,
) {
    val context = LocalContext.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { config -> config.id == trafficConfigId } ?: run {
        navigator.pop()
        return
    }
    val initialRule = ruleLineNumber?.let { lineNumber ->
        config.rawConfig.analyzeShadowrocketConfig().rules.firstOrNull { rule -> rule.lineNumber == lineNumber }
    }
    if (ruleLineNumber != null && initialRule == null) {
        navigator.pop()
        return
    }

    val isFinal = initialRule?.isFinal == true
    val typeOptions = remember { TrafficConfigRuleTypes }
    val editorIdentity = ruleLineNumber ?: config.id * -1
    val editorState = rememberSkipiTrafficConfigRuleEditorState(
        editorIdentity = editorIdentity,
        initialRule = initialRule,
        typeOptions = typeOptions,
    )
    val selectedType = if (isFinal) "FINAL" else typeOptions[editorState.typeIndex]

    var showGeoSitePicker by rememberSaveable(editorIdentity) { mutableStateOf(false) }
    var showGeoIpPicker by rememberSaveable(editorIdentity) { mutableStateOf(false) }
    var showRuleSetPicker by rememberSaveable(editorIdentity) { mutableStateOf(false) }

    val geoSiteSuggestions = remember(appState, trafficConfigId) {
        RoutingSuggestionsProvider.resolveGeoSiteSuggestions(context, appState, trafficConfigId)
    }
    val geoIpSuggestions = remember(appState, trafficConfigId) {
        RoutingSuggestionsProvider.resolveGeoIpSuggestions(context, appState, trafficConfigId, forShadowrocket = true)
    }
    val ruleSetSuggestions = remember(appState, trafficConfigId) {
        RoutingSuggestionsProvider.resolveRuleSetSuggestions(context, appState, trafficConfigId)
    }

    val policySelectorResultKey = remember(trafficConfigId, editorIdentity) {
        "traffic-config-rule-policy-$trafficConfigId-$editorIdentity"
    }
    LaunchedEffect(navigator, policySelectorResultKey) {
        navigator.observeResult<RouteOutboundSelectionResult>(policySelectorResultKey).collect { result ->
            editorState.selectedPolicy = result.tag
            navigator.clearResult(policySelectorResultKey)
        }
    }

    val editor = rememberTrafficConfigRuleEditorController(updateAppState)
    var showInfoBottomSheet by rememberSaveable(editorIdentity) { mutableStateOf(false) }
    val back = { navigator.pop() }

    SkipiTrafficConfigRuleEditorScreen(
        title = stringResource(if (initialRule == null) R.string.configs_rules_add else R.string.configs_rules_edit),
        saveLabel = stringResource(R.string.common_save),
        padding = padding,
        isWideScreen = isWideScreen,
        profileId = trafficConfigId,
        lineNumber = initialRule?.lineNumber,
        isFinal = isFinal,
        editorState = editorState,
        controller = editor,
        onBack = back,
        onPolicyClick = {
            navigator.navigateForResult(
                route = Route.RouteOutboundSelector(
                    selectedTag = editorState.selectedPolicy,
                    resultKey = policySelectorResultKey,
                    trafficConfigId = trafficConfigId,
                ),
                requestKey = policySelectorResultKey,
            )
        },
        networkOptions = RoutingSuggestionsProvider.ShadowrocketNetworkOptions,
        geoSiteSuggestions = geoSiteSuggestions.map { it.tag },
        geoIpSuggestions = geoIpSuggestions.map { it.tag },
        ruleSetPresets = RoutingSuggestionsProvider.RuleSetPresets,
        domainSetPresets = RoutingSuggestionsProvider.DomainSetPresets,
        portPresets = RoutingSuggestionsProvider.PortPresets,
        privateIpPresets = RoutingSuggestionsProvider.PrivateIpPresets,
        userAgentPresets = RoutingSuggestionsProvider.ShadowrocketUserAgentPresets,
        onGeoSitePicker = { showGeoSitePicker = true },
        onGeoIpPicker = { showGeoIpPicker = true },
        onRuleSetPicker = { showRuleSetPicker = true },
        onInfoClick = { showInfoBottomSheet = true },
        platformBackHandler = { onBack -> TrafficConfigRulesBackHandler(onBack) },
        actions = {
            IconButton(onClick = { showInfoBottomSheet = true }) {
                Icon(
                    imageVector = SettingsIcons.Info,
                    contentDescription = stringResource(R.string.routing_info_title),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    )

    GeoAssetPickerDialog(
        show = showGeoSitePicker,
        title = stringResource(R.string.routing_suggestions_geosite),
        items = geoSiteSuggestions,
        onSelect = { item -> editorState.updateValue(if (selectedType == "DOMAIN-SET") item.fullRule else item.tag) },
        onDismissRequest = { showGeoSitePicker = false },
    )

    GeoAssetPickerDialog(
        show = showGeoIpPicker,
        title = stringResource(R.string.routing_suggestions_geoip),
        items = geoIpSuggestions,
        onSelect = { item -> editorState.updateValue(item.tag) },
        onDismissRequest = { showGeoIpPicker = false },
    )

    GeoAssetPickerDialog(
        show = showRuleSetPicker,
        title = "RULE-SET",
        items = ruleSetSuggestions,
        onSelect = { item -> editorState.updateValue(item.fullRule) },
        onDismissRequest = { showRuleSetPicker = false },
    )

    RoutingRulesInfoBottomSheet(
        show = showInfoBottomSheet,
        onDismissRequest = { showInfoBottomSheet = false },
    )
}

@Composable
private fun rememberTrafficConfigRuleEditorController(
    updateAppState: ((AppState) -> AppState) -> Unit,
): TrafficConfigEditorController = remember(updateAppState) {
    TrafficConfigEditorController(
        TrafficConfigEditorProfileStore { profileId, transform ->
            var found = false
            updateAppState { state ->
                if (state.trafficConfigs.none { it.id == profileId }) {
                    state
                } else {
                    found = true
                    state.withUpdatedTrafficConfig(profileId, transform)
                }
            }
            found
        },
    )
}

@Composable
private fun TrafficConfigRulesBackHandler(onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onBack,
    )
}
