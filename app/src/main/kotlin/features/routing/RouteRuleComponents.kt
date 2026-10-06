// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.DefaultRouteOutboundTag
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.proxyServerOutboundTag
import app.skipi.app.routing.upsertRouteRule
import app.skipi.ui.routing.RouteRuleOutboundItem
import app.skipi.ui.routing.SkipiRouteRuleEditorForm
import app.skipi.ui.routing.SkipiRoutingPageScaffold
import features.proxy.server.display.displayNameById
import features.proxy.server.display.displayNameWithGroup
import features.proxy.server.model.isCustomProxyServer
import features.routing.model.RouteRule
import features.routing.ui.GeoAssetPickerDialog
import features.routing.ui.ProcessAppPickerDialog
import features.routing.usecase.RoutingSuggestionsProvider

internal data class RouteRuleOutboundOption(
    val tag: String,
    val label: String,
)

@Composable
fun RouteRuleEditorPage(
    padding: PaddingValues,
    ruleId: Int?,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val context = LocalContext.current
    val initialRule = ruleId?.let { id -> appState.routeRules.firstOrNull { rule -> rule.id == id } }
    if (ruleId != null && initialRule == null) {
        navigator.pop()
        return
    }

    val unknownGroup = stringResource(R.string.common_unknown_group)
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val defaultProxyServerTemplate = stringResource(R.string.routing_default_proxy_server)
    val outboundOptions = (
        listOf(
            RouteRuleOutboundOption(DefaultRouteOutboundTag, stringResource(R.string.routing_outbound_proxy)),
            RouteRuleOutboundOption("direct", stringResource(R.string.routing_outbound_direct)),
            RouteRuleOutboundOption("block", stringResource(R.string.routing_outbound_block)),
        ) + appState.proxyServers.filterNot { it.server.isCustomProxyServer() }.map { server ->
            RouteRuleOutboundOption(
                tag = server.proxyServerOutboundTag(),
                label = server.displayNameWithGroup(
                    defaultProxyServerTemplate = defaultProxyServerTemplate,
                    groupNames = appState.subscriptionGroups.displayNameById(defaultGroupName),
                    unknownGroupName = unknownGroup,
                ),
            )
        }
        ).distinctBy(RouteRuleOutboundOption::tag)

    val editorIdentity = ruleId ?: appState.nextRouteRuleId
    var showProcessPicker by remember(editorIdentity) { mutableStateOf(false) }
    var processPickerSelection by remember(editorIdentity) { mutableStateOf<((String) -> Unit)?>(null) }
    var showGeoSitePicker by remember(editorIdentity) { mutableStateOf(false) }
    var geoSitePickerSelection by remember(editorIdentity) { mutableStateOf<((String) -> Unit)?>(null) }
    var showGeoIpPicker by remember(editorIdentity) { mutableStateOf(false) }
    var geoIpPickerSelection by remember(editorIdentity) { mutableStateOf<((String) -> Unit)?>(null) }
    val geoSiteSuggestions = remember(appState) { RoutingSuggestionsProvider.resolveGeoSiteSuggestions(context, appState) }
    val geoIpSuggestions = remember(appState) { RoutingSuggestionsProvider.resolveGeoIpSuggestions(context, appState) }

    fun save(rule: RouteRule) {
        updateAppState { state ->
            val result = upsertRouteRule(state.routeRules, state.nextRouteRuleId, rule)
            state.copy(routeRules = result.rules, nextRouteRuleId = result.nextRouteRuleId)
        }
        navigator.pop()
    }

    SkipiRoutingPageScaffold(
        title = stringResource(if (initialRule == null) R.string.routing_add_rule else R.string.routing_edit_rule),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
    ) { contentPadding, _ ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            SkipiRouteRuleEditorForm(
                initialRule = initialRule,
                nextRuleId = appState.nextRouteRuleId,
                outboundOptions = outboundOptions.map { RouteRuleOutboundItem(it.tag, it.label) },
                onSave = ::save,
                onCancel = navigator::pop,
                installedAppsLabel = stringResource(R.string.routing_suggestions_select_app),
                geoSitePickerLabel = stringResource(R.string.routing_suggestions_geosite),
                geoIpPickerLabel = stringResource(R.string.routing_suggestions_geoip),
                onRequestInstalledAppPicker = { onSelected ->
                    processPickerSelection = onSelected
                    showProcessPicker = true
                },
                onRequestGeoSitePicker = { onSelected ->
                    geoSitePickerSelection = onSelected
                    showGeoSitePicker = true
                },
                onRequestGeoIpPicker = { onSelected ->
                    geoIpPickerSelection = onSelected
                    showGeoIpPicker = true
                },
            )
            GeoAssetPickerDialog(
                show = showGeoSitePicker,
                title = stringResource(R.string.routing_suggestions_geosite),
                items = geoSiteSuggestions,
                onSelect = { item ->
                    geoSitePickerSelection?.invoke(item.fullRule)
                    geoSitePickerSelection = null
                    showGeoSitePicker = false
                },
                onDismissRequest = { geoSitePickerSelection = null; showGeoSitePicker = false },
            )
            GeoAssetPickerDialog(
                show = showGeoIpPicker,
                title = stringResource(R.string.routing_suggestions_geoip),
                items = geoIpSuggestions,
                onSelect = { item ->
                    geoIpPickerSelection?.invoke(item.fullRule)
                    geoIpPickerSelection = null
                    showGeoIpPicker = false
                },
                onDismissRequest = { geoIpPickerSelection = null; showGeoIpPicker = false },
            )
            ProcessAppPickerDialog(
                show = showProcessPicker,
                onSelect = { packageName ->
                    processPickerSelection?.invoke(packageName)
                    processPickerSelection = null
                    showProcessPicker = false
                },
                onDismissRequest = { processPickerSelection = null; showProcessPicker = false },
            )
        }
    }
}
