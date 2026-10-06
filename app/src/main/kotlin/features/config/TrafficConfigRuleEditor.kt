// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.routing.ui.RoutingRulesInfoBottomSheet
import features.settings.SettingsIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.skipi.ui.config.SkipiTrafficConfigRuleEditorForm
import app.skipi.ui.config.SkipiTrafficConfigRulesList
import app.skipi.ui.config.TrafficConfigRuleListItem
import app.skipi.ui.config.TrafficConfigRuleTypes
import app.navigation.Route
import app.navigation.RouteOutboundSelectionResult
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import features.routing.ui.GeoAssetPickerDialog
import features.routing.usecase.RoutingSuggestionsProvider
import ui.layout.pageScrollModifiers

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
    var localRawConfig by remember(config.rawConfig) { mutableStateOf(config.rawConfig) }
    val analysis = remember(localRawConfig) { localRawConfig.analyzeShadowrocketConfig() }
    val normalRules = analysis.rules.filterNot(ShadowrocketRule::isFinal)
    val finalRule = analysis.rules.firstOrNull(ShadowrocketRule::isFinal)

    val rulesList = remember { mutableStateListOf<TrafficConfigRuleListItem>() }
    var nextItemId by remember { mutableLongStateOf(1L) }

    LaunchedEffect(normalRules) {
        val isUnchanged = rulesList.size == normalRules.size &&
            rulesList.indices.all { i -> rulesList[i].rule.raw == normalRules[i].raw }
        if (!isUnchanged) {
            rulesList.clear()
            rulesList.addAll(
                normalRules.map { rule ->
                    TrafficConfigRuleListItem(id = nextItemId++, rule = rule)
                },
            )
        }
    }

    fun updateRaw(raw: String) {
        localRawConfig = raw
        updateAppState { state -> state.withUpdatedTrafficConfig(config.id) { it.copy(rawConfig = raw) } }
    }

    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    fun moveRules(fromIndex: Int, toIndex: Int) {
        if (fromIndex in rulesList.indices && toIndex in rulesList.indices && fromIndex != toIndex) {
            rulesList.add(toIndex, rulesList.removeAt(fromIndex))
            val updatedRaw = localRawConfig.withShadowrocketRulesReordered(fromIndex, toIndex)
            localRawConfig = updatedRaw
            updateAppState { state -> state.withUpdatedTrafficConfig(config.id) { it.copy(rawConfig = updatedRaw) } }
        }
    }

    var showInfoBottomSheet by rememberSaveable { mutableStateOf(false) }

    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_rules_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onSave = navigator::pop,
        actions = {
            IconButton(onClick = { showInfoBottomSheet = true }) {
                Icon(
                    imageVector = SettingsIcons.Info,
                    contentDescription = stringResource(R.string.routing_info_title),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) { _, listPadding, scrollBehavior ->
        SkipiTrafficConfigRulesList(
            rules = rulesList,
            finalRule = finalRule,
            listState = lazyListState,
            contentPadding = listPadding,
            reorderBottomPadding = padding.calculateBottomPadding(),
            modifier = Modifier.pageScrollModifiers(scrollBehavior),
            onAdd = { navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId)) },
            onEdit = { rule -> navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId, rule.lineNumber)) },
            onDelete = { rule -> updateRaw(config.rawConfig.withoutShadowrocketRuleLine(rule.lineNumber)) },
            onEditFinal = {
                if (finalRule == null) {
                    updateRaw(config.rawConfig.withShadowrocketRuleAdded("FINAL,PROXY"))
                } else {
                    navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId, finalRule.lineNumber))
                }
            },
            onMove = ::moveRules,
            onHapticFeedback = hapticFeedback::performHapticFeedback,
        )
    }

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
    var typeIndex by rememberSaveable(editorIdentity) {
        mutableIntStateOf(typeOptions.indexOf(initialRule?.type ?: "DOMAIN-SUFFIX").coerceAtLeast(0))
    }
    val selectedType = if (isFinal) "FINAL" else typeOptions[typeIndex]
    var value by rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.value.orEmpty()) }
    var selectedPolicy by rememberSaveable(editorIdentity) {
        mutableStateOf(initialRule?.policy?.trim()?.takeIf { it.isNotBlank() } ?: "PROXY")
    }

    val valueState = rememberTextFieldState(initialText = value)

    fun updateValue(newValue: String) {
        value = newValue
        valueState.setTextAndPlaceCursorAtEnd(newValue)
    }

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
            selectedPolicy = result.tag
            navigator.clearResult(policySelectorResultKey)
        }
    }

    fun save() {
        if (selectedPolicy.isBlank() || (!isFinal && value.isBlank())) return
        val line = if (isFinal) "FINAL,$selectedPolicy" else "$selectedType,${value.trim()},$selectedPolicy"
        updateAppState { state ->
            state.withUpdatedTrafficConfig(trafficConfigId) { current ->
                if (initialRule == null) {
                    current.copy(rawConfig = current.rawConfig.withShadowrocketRuleAdded(line))
                } else {
                    current.copy(rawConfig = current.rawConfig.withShadowrocketRuleLine(initialRule.lineNumber, line))
                }
            }
        }
    }
    var showInfoBottomSheet by rememberSaveable { mutableStateOf(false) }

    TrafficConfigFullScreenScaffold(
        title = stringResource(if (initialRule == null) R.string.configs_rules_add else R.string.configs_rules_edit),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = {
            save()
            navigator.pop()
        },
        actions = {
            IconButton(onClick = { showInfoBottomSheet = true }) {
                Icon(
                    imageVector = SettingsIcons.Info,
                    contentDescription = stringResource(R.string.routing_info_title),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) { _, listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigRuleEditorForm(
                    isFinal = isFinal,
                    selectedType = selectedType,
                    typeOptions = typeOptions,
                    onTypeSelected = { typeIndex = it },
                    value = value,
                    valueState = valueState,
                    onValueChange = { value = it },
                    selectedPolicy = selectedPolicy,
                    onPolicyClick = {
                        navigator.navigateForResult(
                            route = Route.RouteOutboundSelector(
                                selectedTag = selectedPolicy,
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
                )
            }
        }
    }

    GeoAssetPickerDialog(
        show = showGeoSitePicker,
        title = stringResource(R.string.routing_suggestions_geosite),
        items = geoSiteSuggestions,
        onSelect = { item ->
            val selected = if (selectedType == "DOMAIN-SET") item.fullRule else item.tag
            updateValue(selected)
        },
        onDismissRequest = { showGeoSitePicker = false },
    )

    GeoAssetPickerDialog(
        show = showGeoIpPicker,
        title = stringResource(R.string.routing_suggestions_geoip),
        items = geoIpSuggestions,
        onSelect = { item -> updateValue(item.tag) },
        onDismissRequest = { showGeoIpPicker = false },
    )

    GeoAssetPickerDialog(
        show = showRuleSetPicker,
        title = "RULE-SET",
        items = ruleSetSuggestions,
        onSelect = { item -> updateValue(item.fullRule) },
        onDismissRequest = { showRuleSetPicker = false },
    )

    RoutingRulesInfoBottomSheet(
        show = showInfoBottomSheet,
        onDismissRequest = { showInfoBottomSheet = false },
    )
}
