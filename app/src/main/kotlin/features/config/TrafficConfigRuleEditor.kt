// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.navigation.RouteOutboundSelectionResult
import sh.calvin.reorderable.ReorderableItem
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import features.routing.ui.GeoAssetPickerDialog
import features.routing.ui.SuggestionChipsRow
import features.routing.usecase.RoutingSuggestionsProvider
import ui.AppTheme
import ui.components.draggedCardShadow
import ui.components.longPressReorderDragHandle
import ui.components.rememberSkipiReorderableLazyListState
import ui.components.rememberReorderableScrollThresholdPadding

private data class TrafficConfigRuleItem(
    val id: Long,
    val rule: ShadowrocketRule,
)

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

    val rulesList = remember { mutableStateListOf<TrafficConfigRuleItem>() }
    var nextItemId by remember { mutableLongStateOf(1L) }

    LaunchedEffect(normalRules) {
        if (rulesList.map { it.rule.raw } != normalRules.map { it.raw }) {
            rulesList.clear()
            rulesList.addAll(
                normalRules.map { rule ->
                    TrafficConfigRuleItem(id = nextItemId++, rule = rule)
                },
            )
        }
    }

    fun updateRaw(raw: String) {
        localRawConfig = raw
        updateAppState { state -> state.withUpdatedTrafficConfig(config.id) { it.copy(rawConfig = raw) } }
    }

    val lazyListState = rememberLazyListState()
    val listBottomPadding = padding.calculateBottomPadding()
    val reorderableLazyListState = rememberSkipiReorderableLazyListState(
        lazyListState = lazyListState,
        itemCount = rulesList.size,
        itemIndexOffset = 2,
        scrollThresholdPadding = rememberReorderableScrollThresholdPadding(
            bottom = listBottomPadding,
        ),
    ) { fromIndex, toIndex ->
        if (fromIndex in rulesList.indices && toIndex in rulesList.indices && fromIndex != toIndex) {
            rulesList.add(toIndex, rulesList.removeAt(fromIndex))
            val sourceLines = localRawConfig.lines().toMutableList()
            val lineIndices = normalRules.map { it.lineNumber - 1 }
            rulesList.forEachIndexed { i, item ->
                if (i in lineIndices.indices) {
                    sourceLines[lineIndices[i]] = item.rule.raw
                }
            }
            val updatedRaw = sourceLines.joinToString("\n").trimEnd() + "\n"
            localRawConfig = updatedRaw
            updateAppState { state -> state.withUpdatedTrafficConfig(config.id) { it.copy(rawConfig = updatedRaw) } }
        }
    }

    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_rules_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onSave = navigator::pop,
    ) { listPadding ->
        LazyColumn(
            state = lazyListState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "order") {
                Text(
                    text = stringResource(R.string.configs_rules_order),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                )
            }
            item(key = "add") {
                TextButton(
                    text = stringResource(R.string.configs_rules_add),
                    onClick = {
                        navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            items(items = rulesList, key = { item -> item.id }) { item ->
                ReorderableItem(
                    state = reorderableLazyListState.reorderableState,
                    key = item.id,
                    enabled = rulesList.size > 1,
                    animateItemModifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = folmeSpring(damping = 0.9f, response = 0.38f),
                    ),
                ) { isDragging ->
                    VisualRuleCard(
                        rule = item.rule,
                        isDragging = isDragging,
                        dragModifier = Modifier.longPressReorderDragHandle(
                            scope = this,
                            enabled = rulesList.size > 1,
                            state = reorderableLazyListState,
                        ),
                        onEdit = {
                            navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId, item.rule.lineNumber))
                        },
                        onDelete = { updateRaw(config.rawConfig.withoutShadowrocketRuleLine(item.rule.lineNumber)) },
                    )
                }
            }
            item(key = "fallback") {
                FinalRuleCard(
                    rule = finalRule,
                    onEdit = {
                        if (finalRule == null) {
                            updateRaw(config.rawConfig.withShadowrocketRuleAdded("FINAL,PROXY"))
                        } else {
                            navigator.push(Route.TrafficConfigRuleEditor(trafficConfigId, finalRule.lineNumber))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VisualRuleCard(
    rule: ShadowrocketRule,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "visualRuleDragScale",
    )
    val animatedShadowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "visualRuleDragShadowAlpha",
    )
    val shadowColor = AppTheme.colors.onSurface.copy(alpha = 0.20f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .draggedCardShadow(
                alpha = animatedShadowAlpha,
                color = shadowColor,
            )
            .then(dragModifier)
            .clickable(onClick = onEdit),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${rule.type}, ${rule.value}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.policy,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = stringResource(R.string.configs_edit),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FinalRuleCard(
    rule: ShadowrocketRule?,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FINAL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule?.policy ?: stringResource(R.string.configs_rules_final_missing),
                    fontSize = 12.sp,
                    color = if (rule != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = stringResource(R.string.configs_edit),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
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
    val typeOptions = remember { VisualRuleTypes }
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
    TrafficConfigFullScreenScaffold(
        title = stringResource(if (initialRule == null) R.string.configs_rules_add else R.string.configs_rules_edit),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = {
            save()
            navigator.pop()
        },
    ) { listPadding ->
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (!isFinal) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                    ) {
                        WindowDropdownPreference(
                            title = stringResource(R.string.configs_rules_type),
                            items = typeOptions,
                            selectedIndex = typeIndex,
                            onSelectedIndexChange = { typeIndex = it },
                        )
                    }
                    when (selectedType) {
                        "NETWORK" -> {
                            val networkOptions = RoutingSuggestionsProvider.ShadowrocketNetworkOptions
                            val networkIndex = networkOptions.indexOf(value.trim().lowercase())
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                            ) {
                                WindowDropdownPreference(
                                    title = stringResource(R.string.routing_network_label),
                                    items = networkOptions,
                                    selectedIndex = networkIndex.coerceAtLeast(0),
                                    onSelectedIndexChange = { idx -> updateValue(networkOptions[idx]) },
                                )
                            }
                            SuggestionChipsRow(
                                chips = networkOptions,
                                onChipClick = { updateValue(it) },
                                selectedChip = value.trim().lowercase(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "GEOIP" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString().uppercase() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = geoIpSuggestions.take(15).map { it.tag },
                                onChipClick = { updateValue(it) },
                                actionButtonText = stringResource(R.string.routing_suggestions_geoip),
                                onActionClick = { showGeoIpPicker = true },
                                selectedChip = value.trim().uppercase(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = geoSiteSuggestions.take(12).map { it.tag },
                                onChipClick = { updateValue(it) },
                                actionButtonText = stringResource(R.string.routing_suggestions_geosite),
                                onActionClick = { showGeoSitePicker = true },
                                selectedChip = value.trim().lowercase(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "RULE-SET" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = RoutingSuggestionsProvider.RuleSetPresets,
                                onChipClick = { updateValue(it) },
                                actionButtonText = stringResource(R.string.routing_suggestions_title),
                                onActionClick = { showRuleSetPicker = true },
                                selectedChip = value.trim(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "DOMAIN-SET" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = RoutingSuggestionsProvider.DomainSetPresets,
                                onChipClick = { updateValue(it) },
                                actionButtonText = stringResource(R.string.routing_suggestions_geosite),
                                onActionClick = { showGeoSitePicker = true },
                                selectedChip = value.trim(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "DST-PORT" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = RoutingSuggestionsProvider.PortPresets,
                                onChipClick = { updateValue(it) },
                                selectedChip = value.trim(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "IP-CIDR" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = RoutingSuggestionsProvider.PrivateIpPresets,
                                onChipClick = { updateValue(it) },
                                selectedChip = value.trim(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        "USER-AGENT" -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                            SuggestionChipsRow(
                                chips = RoutingSuggestionsProvider.ShadowrocketUserAgentPresets,
                                onChipClick = { updateValue(it) },
                                selectedChip = value.trim(),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        else -> {
                            TextField(
                                state = valueState,
                                inputTransformation = { value = asCharSequence().toString() },
                                label = stringResource(R.string.configs_rules_value),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            )
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.configs_rules_policy),
                        summary = selectedPolicy,
                        onClick = {
                            navigator.navigateForResult(
                                route = Route.RouteOutboundSelector(
                                    selectedTag = selectedPolicy,
                                    resultKey = policySelectorResultKey,
                                    trafficConfigId = trafficConfigId,
                                ),
                                requestKey = policySelectorResultKey,
                            )
                        },
                    )
                }
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
}

private val VisualRuleTypes = listOf(
    "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "IP-CIDR", "GEOIP", "DST-PORT",
    "NETWORK", "RULE-SET", "DOMAIN-SET", "USER-AGENT", "URL-REGEX",
)
