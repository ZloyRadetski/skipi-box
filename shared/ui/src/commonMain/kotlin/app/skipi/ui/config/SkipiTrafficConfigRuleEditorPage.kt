// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.configs_rules_edit
import app.skipi.ui.resources.routing_info_title
import app.skipi.ui.resources.routing_suggestions_geosite
import app.skipi.ui.resources.routing_suggestions_geoip
import app.skipi.ui.routing.SkipiGeoAssetPickerDialog
import app.skipi.ui.routing.SkipiRoutingRulesInfoBottomSheet
import app.skipi.ui.settings.SkipiSettingsIcons
import features.config.ShadowrocketRule
import features.routing.usecase.GeoSuggestionItem
import features.routing.usecase.RoutingSuggestionPresets
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared form, suggestion dialogs, and routing help; hosts supply data, navigation, and Back handling. */
@Composable
fun SkipiTrafficConfigRuleEditorPage(
    padding: PaddingValues,
    isWideScreen: Boolean,
    profileId: Int,
    editorIdentity: Int,
    initialRule: ShadowrocketRule?,
    editorState: SkipiTrafficConfigRuleEditorState,
    controller: TrafficConfigEditorController,
    geoSiteSuggestions: List<GeoSuggestionItem>,
    geoIpSuggestions: List<GeoSuggestionItem>,
    ruleSetSuggestions: List<GeoSuggestionItem>,
    onBack: () -> Unit,
    onPolicyClick: () -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    statusContent: (@Composable () -> Unit)? = null,
) {
    val isFinal = initialRule?.isFinal == true
    val selectedType = if (isFinal) "FINAL" else TrafficConfigRuleTypes[editorState.typeIndex]
    var showGeoSitePicker by rememberSaveable(editorIdentity) { mutableStateOf(false) }
    var showGeoIpPicker by rememberSaveable(editorIdentity) { mutableStateOf(false) }
    var showRuleSetPicker by rememberSaveable(editorIdentity) { mutableStateOf(false) }
    var showInfoBottomSheet by rememberSaveable(editorIdentity) { mutableStateOf(false) }

    SkipiTrafficConfigRuleEditorScreen(
        title = stringResource(if (initialRule == null) Res.string.configs_rules_add else Res.string.configs_rules_edit),
        saveLabel = stringResource(Res.string.common_save),
        padding = padding,
        isWideScreen = isWideScreen,
        profileId = profileId,
        lineNumber = initialRule?.lineNumber,
        isFinal = isFinal,
        editorState = editorState,
        controller = controller,
        onBack = onBack,
        onPolicyClick = onPolicyClick,
        networkOptions = RoutingSuggestionPresets.ShadowrocketNetworkOptions,
        geoSiteSuggestions = geoSiteSuggestions.map(GeoSuggestionItem::tag),
        geoIpSuggestions = geoIpSuggestions.map(GeoSuggestionItem::tag),
        ruleSetPresets = RoutingSuggestionPresets.RuleSetPresets,
        domainSetPresets = RoutingSuggestionPresets.DomainSetPresets,
        portPresets = RoutingSuggestionPresets.PortPresets,
        privateIpPresets = RoutingSuggestionPresets.PrivateIpPresets,
        userAgentPresets = RoutingSuggestionPresets.ShadowrocketUserAgentPresets,
        onGeoSitePicker = { showGeoSitePicker = true },
        onGeoIpPicker = { showGeoIpPicker = true },
        onRuleSetPicker = { showRuleSetPicker = true },
        onInfoClick = { showInfoBottomSheet = true },
        platformBackHandler = platformBackHandler,
        actions = {
            IconButton(onClick = { showInfoBottomSheet = true }) {
                Icon(
                    imageVector = SkipiSettingsIcons.Info,
                    contentDescription = stringResource(Res.string.routing_info_title),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        statusContent = statusContent,
    )

    SkipiGeoAssetPickerDialog(
        show = showGeoSitePicker,
        title = stringResource(Res.string.routing_suggestions_geosite),
        items = geoSiteSuggestions,
        onSelect = { item -> editorState.updateValue(if (selectedType == "DOMAIN-SET") item.fullRule else item.tag) },
        onDismissRequest = { showGeoSitePicker = false },
    )
    SkipiGeoAssetPickerDialog(
        show = showGeoIpPicker,
        title = stringResource(Res.string.routing_suggestions_geoip),
        items = geoIpSuggestions,
        onSelect = { item -> editorState.updateValue(item.tag) },
        onDismissRequest = { showGeoIpPicker = false },
    )
    SkipiGeoAssetPickerDialog(
        show = showRuleSetPicker,
        title = "RULE-SET",
        items = ruleSetSuggestions,
        onSelect = { item -> editorState.updateValue(item.fullRule) },
        onDismissRequest = { showRuleSetPicker = false },
    )
    SkipiRoutingRulesInfoBottomSheet(
        show = showInfoBottomSheet,
        onDismissRequest = { showInfoBottomSheet = false },
    )
}
