// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.SkipiDropdownPreference
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_rules_policy
import app.skipi.ui.resources.configs_rules_type
import app.skipi.ui.resources.configs_rules_type_and
import app.skipi.ui.resources.configs_rules_type_or
import app.skipi.ui.resources.configs_rules_unsupported_on_android
import app.skipi.ui.resources.configs_rules_value
import app.skipi.ui.resources.routing_info_title
import app.skipi.ui.resources.routing_network_label
import app.skipi.ui.resources.routing_suggestions_geosite
import app.skipi.ui.resources.routing_suggestions_geoip
import app.skipi.ui.resources.routing_suggestions_title
import app.skipi.ui.theme.SkipiTheme
import features.config.ShadowrocketLogicalOperator
import features.config.keyword
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val TypesWithSuggestionChips = setOf(
    "GEOIP", "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "RULE-SET", "DOMAIN-SET",
    "DST-PORT", "IP-CIDR", "USER-AGENT",
)

val TrafficConfigRuleTypes = listOf(
    "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "IP-CIDR", "GEOIP", "DST-PORT",
    "NETWORK", "RULE-SET", "DOMAIN-SET", "USER-AGENT", "URL-REGEX", "OR", "AND",
)

/** Profile rule form. Platform navigation, asset loading, and dialogs remain host callbacks. */
@Composable
fun SkipiTrafficConfigRuleEditorForm(
    isFinal: Boolean,
    selectedType: String,
    typeOptions: List<String>,
    onTypeSelected: (Int) -> Unit,
    value: String,
    valueState: TextFieldState,
    onValueChange: (String) -> Unit,
    selectedPolicy: String,
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
    modifier: Modifier = Modifier,
) {
    fun updateValue(newValue: String) {
        onValueChange(newValue)
        valueState.setTextAndPlaceCursorAtEnd(newValue)
    }

    fun applyIntent(intent: TrafficConfigRuleIntent) {
        val index = typeOptions.indexOf(intent.type)
        if (index >= 0) onTypeSelected(index)
        onValueChange(intent.value)
    }

    Column(modifier = modifier) {
        if (!isFinal) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
            ) {
                val anyLabel = stringResource(Res.string.configs_rules_type_or)
                val allLabel = stringResource(Res.string.configs_rules_type_and)
                SkipiDropdownPreference(
                    title = stringResource(Res.string.configs_rules_type),
                    items = typeOptions.map { type ->
                        when (type) {
                            "OR" -> anyLabel
                            "AND" -> allLabel
                            else -> type
                        }
                    },
                    selectedIndex = typeOptions.indexOf(selectedType).coerceAtLeast(0),
                    onSelectedIndexChange = onTypeSelected,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.routing_info_title),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onInfoClick)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            val isLogical = selectedType == "OR" || selectedType == "AND"
            val showsValueField = !isLogical && selectedType != "NETWORK"
            // One value field for every plain type, at one place in the composition. A field per type would be
            // recreated whenever the type changes (for example when typing "keyword:" switches it), which drops
            // the focus and closes the Android keyboard.
            if (showsValueField) {
                val hasChips = selectedType in TypesWithSuggestionChips
                RuleValueField(
                    state = valueState,
                    label = stringResource(Res.string.configs_rules_value),
                    onValueChange = onValueChange,
                    onIntent = ::applyIntent,
                    uppercase = selectedType == "GEOIP",
                    bottomPadding = if (hasChips) 6.dp else 10.dp,
                )
            }
            when (selectedType) {
                "OR", "AND" -> SkipiTrafficConfigLogicalConditionEditor(
                    operator = if (selectedType == "AND") ShadowrocketLogicalOperator.And else ShadowrocketLogicalOperator.Or,
                    bodyText = value,
                    onBodyChange = onValueChange,
                    onOperatorChange = { operator ->
                        typeOptions.indexOf(operator.keyword).takeIf { it >= 0 }?.let(onTypeSelected)
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                "NETWORK" -> {
                    val selectedIndex = networkOptions.indexOf(value.trim().lowercase())
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
                    ) {
                        SkipiDropdownPreference(
                            title = stringResource(Res.string.routing_network_label),
                            items = networkOptions,
                            selectedIndex = selectedIndex.coerceAtLeast(0),
                            onSelectedIndexChange = { index -> updateValue(networkOptions[index]) },
                        )
                    }
                    SuggestionChips(
                        chips = networkOptions,
                        onChipClick = ::updateValue,
                        selectedChip = value.trim().lowercase(),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                "GEOIP" -> SuggestionChips(
                    chips = geoIpSuggestions.take(15),
                    onChipClick = ::updateValue,
                    actionButtonText = stringResource(Res.string.routing_suggestions_geoip),
                    onActionClick = onGeoIpPicker,
                    selectedChip = value.trim().uppercase(),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD" -> SuggestionChips(
                    chips = geoSiteSuggestions.take(12),
                    onChipClick = ::updateValue,
                    actionButtonText = stringResource(Res.string.routing_suggestions_geosite),
                    onActionClick = onGeoSitePicker,
                    selectedChip = value.trim().lowercase(),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                "RULE-SET" -> SuggestionChips(
                    chips = ruleSetPresets,
                    onChipClick = ::updateValue,
                    actionButtonText = stringResource(Res.string.routing_suggestions_title),
                    onActionClick = onRuleSetPicker,
                    selectedChip = value.trim(),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                "DOMAIN-SET" -> SuggestionChips(
                    chips = domainSetPresets,
                    onChipClick = ::updateValue,
                    actionButtonText = stringResource(Res.string.routing_suggestions_geosite),
                    onActionClick = onGeoSitePicker,
                    selectedChip = value.trim(),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                "DST-PORT" -> SuggestionChips(portPresets, ::updateValue, selectedChip = value.trim(), modifier = Modifier.padding(bottom = 10.dp))
                "IP-CIDR" -> SuggestionChips(privateIpPresets, ::updateValue, selectedChip = value.trim(), modifier = Modifier.padding(bottom = 10.dp))
                "USER-AGENT" -> SuggestionChips(userAgentPresets, ::updateValue, selectedChip = value.trim(), modifier = Modifier.padding(bottom = 10.dp))
                else -> Unit
            }
            if (selectedType in setOf("USER-AGENT", "URL-REGEX")) {
                Text(
                    text = stringResource(Res.string.configs_rules_unsupported_on_android),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
        ) {
            ArrowPreference(
                title = stringResource(Res.string.configs_rules_policy),
                summary = selectedPolicy,
                onClick = onPolicyClick,
            )
        }
    }
}

@Composable
private fun RuleValueField(
    state: TextFieldState,
    label: String,
    onValueChange: (String) -> Unit,
    onIntent: (TrafficConfigRuleIntent) -> Unit,
    uppercase: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    TextField(
        state = state,
        inputTransformation = {
            val typed = asCharSequence().toString()
            val intent = detectTrafficConfigRuleIntent(typed)
            if (intent != null) {
                // "keyword:google" -> type DOMAIN-KEYWORD, value "google"; the field keeps only the value.
                replace(0, length, intent.value)
                onIntent(intent)
            } else {
                onValueChange(if (uppercase) typed.uppercase() else typed)
            }
        },
        label = label,
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
    )
}

@Composable
private fun SuggestionChips(
    chips: List<String>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    selectedChip: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actionButtonText != null && onActionClick != null) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SkipiTheme.colors.accent)
                    .clickable(onClick = onActionClick).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 12.sp,
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                    color = SkipiTheme.colors.onAccent,
                )
            }
        }
        chips.forEach { chip ->
            val selected = selectedChip == chip
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (selected) SkipiTheme.colors.accent else SkipiTheme.colors.surfaceVariant)
                    .clickable { onChipClick(chip) }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = chip,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) SkipiTheme.colors.onAccent else MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
