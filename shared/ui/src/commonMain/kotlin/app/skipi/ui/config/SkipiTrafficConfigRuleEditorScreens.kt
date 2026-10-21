// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigRuleDraft
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.routing_info_title
import app.skipi.ui.routing.SkipiRoutingRulesInfoBottomSheet
import app.skipi.ui.settings.SkipiSettingsIcons
import features.config.ShadowrocketRule
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.withShadowrocketRuleAdded
import features.config.withShadowrocketRuleLine
import features.config.withShadowrocketRulesReordered
import features.config.withoutShadowrocketRuleLine
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared state for editable fields; hosts own async policy results and suggestion data. */
class SkipiTrafficConfigRuleEditorState internal constructor(
    private val typeIndexState: MutableIntState,
    private val valueState: MutableState<String>,
    private val policyState: MutableState<String>,
    val textFieldState: TextFieldState,
) {
    var typeIndex: Int
        get() = typeIndexState.intValue
        set(value) { typeIndexState.intValue = value }

    var value: String
        get() = valueState.value
        set(value) { valueState.value = value }

    var selectedPolicy: String
        get() = policyState.value
        set(value) { policyState.value = value }

    fun updateValue(value: String) {
        this.value = value
        textFieldState.setTextAndPlaceCursorAtEnd(value)
    }
}

/** Uses the Android editor's saveable identity and initial-value rules. */
@Composable
fun rememberSkipiTrafficConfigRuleEditorState(
    editorIdentity: Int,
    initialRule: ShadowrocketRule?,
    typeOptions: List<String> = TrafficConfigRuleTypes,
): SkipiTrafficConfigRuleEditorState {
    val typeIndex = rememberSaveable(editorIdentity) {
        mutableIntStateOf(typeOptions.indexOf(initialRule?.type ?: "DOMAIN-SUFFIX").coerceAtLeast(0))
    }
    val value = rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.value.orEmpty()) }
    val policy = rememberSaveable(editorIdentity) {
        mutableStateOf(initialRule?.policy?.trim()?.takeIf(String::isNotBlank) ?: "PROXY")
    }
    val textFieldState = rememberTextFieldState(initialText = value.value)
    return remember(editorIdentity) {
        SkipiTrafficConfigRuleEditorState(typeIndex, value, policy, textFieldState)
    }
}

/** Stateful shared rules list. The host supplies only navigation and platform haptics. */
@Composable
fun SkipiTrafficConfigRulesEditorScreen(
    title: String,
    saveLabel: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ShadowrocketRule) -> Unit,
    onHapticFeedback: (HapticFeedbackType) -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
) {
    var localRawConfig by remember(profile.rawConfig) { mutableStateOf(profile.rawConfig) }
    val analysis = remember(localRawConfig) { localRawConfig.analyzeShadowrocketConfig() }
    val normalRules = analysis.rules.filterNot(ShadowrocketRule::isFinal)
    val finalRule = analysis.rules.firstOrNull(ShadowrocketRule::isFinal)
    var showInfoBottomSheet by rememberSaveable(profile.id) { mutableStateOf(false) }

    val rulesList = remember { mutableStateListOf<TrafficConfigRuleListItem>() }
    var nextItemId by remember { mutableLongStateOf(1L) }
    LaunchedEffect(normalRules) {
        val isUnchanged = rulesList.size == normalRules.size &&
            rulesList.indices.all { index -> rulesList[index].rule.raw == normalRules[index].raw }
        if (!isUnchanged) {
            rulesList.clear()
            rulesList.addAll(
                normalRules.map { rule -> TrafficConfigRuleListItem(id = nextItemId++, rule = rule) },
            )
        }
    }

    fun updateRaw(raw: String) {
        localRawConfig = raw
        controller.saveRuleDocument(profile.id, raw)
    }

    fun moveRules(fromIndex: Int, toIndex: Int) {
        if (fromIndex in rulesList.indices && toIndex in rulesList.indices && fromIndex != toIndex) {
            rulesList.add(toIndex, rulesList.removeAt(fromIndex))
            updateRaw(localRawConfig.withShadowrocketRulesReordered(fromIndex, toIndex))
        }
    }

    platformBackHandler(onBack)
    val listState = rememberLazyListState()
    SkipiTrafficConfigFullScreenScaffold(
        title = title,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = saveLabel,
        onBack = onBack,
        onSave = onSave,
        actions = {
            actions?.invoke()
            IconButton(onClick = { showInfoBottomSheet = true }) {
                Icon(
                    imageVector = SkipiSettingsIcons.Info,
                    contentDescription = stringResource(Res.string.routing_info_title),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) { _, listPadding, scrollBehavior ->
        SkipiTrafficConfigRulesList(
            rules = rulesList,
            finalRule = finalRule,
            listState = listState,
            contentPadding = listPadding,
            reorderBottomPadding = padding.calculateBottomPadding(),
            modifier = Modifier.pageScrollModifiers(scrollBehavior),
            onAdd = onAdd,
            onEdit = onEdit,
            onDelete = { rule ->
                updateRaw(profile.rawConfig.withoutShadowrocketRuleLine(rule.lineNumber))
            },
            onEditFinal = {
                if (finalRule == null) {
                    updateRaw(profile.rawConfig.withShadowrocketRuleAdded("FINAL,PROXY"))
                } else {
                    onEdit(finalRule)
                }
            },
            onMove = ::moveRules,
            onHapticFeedback = onHapticFeedback,
        )
    }
    SkipiRoutingRulesInfoBottomSheet(
        show = showInfoBottomSheet,
        onDismissRequest = { showInfoBottomSheet = false },
    )
}

/** Shared rule form and save-on-Back behavior; hosts supply suggestions and platform Back handling. */
@Composable
fun SkipiTrafficConfigRuleEditorScreen(
    title: String,
    saveLabel: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    profileId: Int,
    lineNumber: Int?,
    isFinal: Boolean,
    editorState: SkipiTrafficConfigRuleEditorState,
    controller: TrafficConfigEditorController,
    onBack: () -> Unit,
    onPolicyClick: () -> Unit,
    networkOptions: List<String>,
    geoSiteSuggestions: List<String>,
    geoIpSuggestions: List<String>,
    ruleSetPresets: List<String>,
    domainSetPresets: List<String>,
    portPresets: List<String>,
    privateIpPresets: List<String>,
    userAgentPresets: List<String>,
    onGeoSitePicker: () -> Unit,
    onGeoIpPicker: () -> Unit,
    onRuleSetPicker: () -> Unit,
    onInfoClick: () -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
    statusContent: (@Composable () -> Unit)? = null,
) {
    val typeOptions = TrafficConfigRuleTypes
    val selectedType = if (isFinal) "FINAL" else typeOptions[editorState.typeIndex]
    val saveAndBack: () -> Unit = {
        controller.saveRuleDraftThenNavigate(
            profileId = profileId,
            draft = TrafficConfigRuleDraft(
                lineNumber = lineNumber,
                isFinal = isFinal,
                type = selectedType,
                value = editorState.value,
                policy = editorState.selectedPolicy,
            ),
            onNavigate = onBack,
        )
        Unit
    }
    platformBackHandler(saveAndBack)
    SkipiTrafficConfigFullScreenScaffold(
        title = title,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = saveLabel,
        onBack = saveAndBack,
        actions = actions,
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
                    onTypeSelected = { editorState.typeIndex = it },
                    value = editorState.value,
                    valueState = editorState.textFieldState,
                    onValueChange = { editorState.value = it },
                    selectedPolicy = editorState.selectedPolicy,
                    onPolicyClick = onPolicyClick,
                    networkOptions = networkOptions,
                    geoSiteSuggestions = geoSiteSuggestions,
                    geoIpSuggestions = geoIpSuggestions,
                    ruleSetPresets = ruleSetPresets,
                    domainSetPresets = domainSetPresets,
                    portPresets = portPresets,
                    privateIpPresets = privateIpPresets,
                    userAgentPresets = userAgentPresets,
                    onGeoSitePicker = onGeoSitePicker,
                    onGeoIpPicker = onGeoIpPicker,
                    onRuleSetPicker = onRuleSetPicker,
                    onInfoClick = onInfoClick,
                )
            }
            statusContent?.let { content ->
                item(key = "rule_editor_status") { content() }
            }
        }
    }
}
