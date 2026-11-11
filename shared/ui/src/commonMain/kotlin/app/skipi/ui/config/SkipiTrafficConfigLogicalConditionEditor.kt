// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.SkipiDropdownPreference
import app.skipi.ui.resources.configs_rules_all_of
import app.skipi.ui.resources.configs_rules_all_of_hint
import app.skipi.ui.resources.configs_rules_any_of
import app.skipi.ui.resources.configs_rules_any_of_hint
import app.skipi.ui.resources.configs_rules_summary_empty
import app.skipi.ui.resources.configs_rules_summary_title
import app.skipi.ui.resources.configs_rules_word_and
import app.skipi.ui.resources.configs_rules_word_or
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.configs_rules_add_condition
import app.skipi.ui.resources.configs_rules_add_group
import app.skipi.ui.resources.configs_rules_group_operator
import app.skipi.ui.resources.configs_rules_logical_unsupported
import app.skipi.ui.resources.configs_rules_type
import app.skipi.ui.resources.configs_rules_value
import app.skipi.ui.theme.SkipiTheme
import features.config.ShadowrocketCondition
import features.config.ShadowrocketLogicalOperator
import features.config.parseShadowrocketConditionBody
import features.config.readableSummary
import features.config.toStoredBodyText
import features.config.xrayUnsupportedReasonForCondition
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Rule types a condition inside an OR / AND rule can use. */
val TrafficConfigLogicalLeafTypes = listOf(
    "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "GEOSITE", "IP-CIDR", "GEOIP", "DST-PORT",
    "NETWORK", "RULE-SET", "DOMAIN-SET",
)

/** Deepest nesting of groups the editor offers; deeper trees read badly and rarely carry meaning. */
private const val MaxEditorGroupDepth = 3

private val LogicalOperators = listOf(ShadowrocketLogicalOperator.Or, ShadowrocketLogicalOperator.And)

/** Value to store when a plain rule becomes an OR / AND rule: its one condition becomes the first child. */
fun logicalBodyFromPlainRule(type: String, value: String): String =
    if (value.isBlank() || type !in TrafficConfigLogicalLeafTypes) {
        ""
    } else {
        ShadowrocketCondition.Group(ShadowrocketLogicalOperator.Or, listOf(ShadowrocketCondition.Leaf(type, value.trim())))
            .toStoredBodyText()
    }

/** First condition of an OR / AND rule, for turning it back into a plain rule. */
fun plainRuleFromLogicalBody(operator: ShadowrocketLogicalOperator, body: String): Pair<String, String>? =
    (parseShadowrocketConditionBody(operator, body)?.children?.firstOrNull() as? ShadowrocketCondition.Leaf)
        ?.let { it.type to it.value }

/**
 * Tree editor for the conditions of an OR / AND rule. The tree lives in local state so half-typed (blank) conditions
 * survive recomposition; [onBodyChange] receives only the usable part, serialised as `((A),(B))`.
 *
 * Every group starts with a plain-language choice, "any of" or "all of", explained in one sentence, and the
 * conditions of a group are joined by that word, so the structure can be read without knowing OR / AND.
 */
@Composable
fun SkipiTrafficConfigLogicalConditionEditor(
    operator: ShadowrocketLogicalOperator,
    bodyText: String,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOperatorChange: (ShadowrocketLogicalOperator) -> Unit = {},
) {
    var children by remember {
        mutableStateOf(parseShadowrocketConditionBody(operator, bodyText)?.children.orEmpty())
    }
    val root = ShadowrocketCondition.Group(operator, children)
    val unsupported = xrayUnsupportedReasonForCondition(operator, root) != null
    val orWord = stringResource(Res.string.configs_rules_word_or)
    val andWord = stringResource(Res.string.configs_rules_word_and)
    val summary = root.readableSummary(orWord = orWord, andWord = andWord)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperatorChoice(operator = operator, onChange = onOperatorChange)
        GroupContent(
            group = root,
            depth = 1,
            onChange = { updated ->
                children = updated.children
                onBodyChange(updated.toStoredBodyText())
            },
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surfaceVariant),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.configs_rules_summary_title),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = summary.ifEmpty { stringResource(Res.string.configs_rules_summary_empty) },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
        if (unsupported) {
            Text(
                text = stringResource(Res.string.configs_rules_logical_unsupported),
                color = MiuixTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** "Any of / all of" switch with the one-sentence meaning of the selected choice underneath. */
@Composable
private fun OperatorChoice(
    operator: ShadowrocketLogicalOperator,
    onChange: (ShadowrocketLogicalOperator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(Res.string.configs_rules_any_of),
        stringResource(Res.string.configs_rules_all_of),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TabRowWithContour(
            tabs = labels,
            selectedTabIndex = LogicalOperators.indexOf(operator).coerceAtLeast(0),
            onTabSelected = { onChange(LogicalOperators[it]) },
            colors = TabRowDefaults.tabRowColors(
                selectedBackgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f),
                selectedContentColor = MiuixTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                if (operator == ShadowrocketLogicalOperator.Or) Res.string.configs_rules_any_of_hint else Res.string.configs_rules_all_of_hint,
            ),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun GroupContent(
    group: ShadowrocketCondition.Group,
    depth: Int,
    onChange: (ShadowrocketCondition.Group) -> Unit,
) {
    val joinWord = stringResource(
        if (group.operator == ShadowrocketLogicalOperator.Or) Res.string.configs_rules_word_or else Res.string.configs_rules_word_and,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        group.children.forEachIndexed { index, child ->
            if (index > 0) Connector(joinWord)
            val replaceChild = { replacement: ShadowrocketCondition? ->
                val updated = group.children.toMutableList()
                if (replacement == null) updated.removeAt(index) else updated[index] = replacement
                onChange(group.copy(children = updated))
            }
            when (child) {
                is ShadowrocketCondition.Leaf -> LeafRow(child, onChange = replaceChild)
                is ShadowrocketCondition.Group -> NestedGroup(child, depth, onChange = replaceChild)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            ActionChip(stringResource(Res.string.configs_rules_add_condition)) {
                onChange(group.copy(children = group.children + ShadowrocketCondition.Leaf("DOMAIN-SUFFIX", "")))
            }
            if (depth < MaxEditorGroupDepth) {
                ActionChip(stringResource(Res.string.configs_rules_add_group)) {
                    // A group inside "any of" is most often a set that must hold together, and the other way round.
                    val nested = ShadowrocketCondition.Group(
                        group.operator.other(),
                        listOf(ShadowrocketCondition.Leaf("DOMAIN-SUFFIX", "")),
                    )
                    onChange(group.copy(children = group.children + nested))
                }
            }
        }
    }
}

internal fun ShadowrocketLogicalOperator.other(): ShadowrocketLogicalOperator =
    if (this == ShadowrocketLogicalOperator.Or) ShadowrocketLogicalOperator.And else ShadowrocketLogicalOperator.Or

/** The word that joins two neighbouring conditions, centred on a thin line. */
@Composable
private fun Connector(word: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
        Text(
            text = word,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
    }
}

@Composable
private fun NestedGroup(
    group: ShadowrocketCondition.Group,
    depth: Int,
    onChange: (ShadowrocketCondition?) -> Unit,
) {
    val accent = MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // The coloured bar marks everything inside it as one unit that is judged before it is joined to its neighbours.
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent.copy(alpha = 0.55f)))
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.configs_rules_group_operator),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange(null) }) {
                        Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.common_delete), tint = MiuixTheme.colorScheme.onSurface)
                    }
                }
                OperatorChoice(
                    operator = group.operator,
                    onChange = { onChange(group.copy(operator = it)) },
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
                Column(Modifier.padding(end = 8.dp)) {
                    GroupContent(group, depth + 1, onChange = { onChange(it) })
                }
            }
        }
    }
}

@Composable
private fun LeafRow(
    leaf: ShadowrocketCondition.Leaf,
    onChange: (ShadowrocketCondition.Leaf?) -> Unit,
) {
    val types = if (leaf.type in TrafficConfigLogicalLeafTypes) {
        TrafficConfigLogicalLeafTypes
    } else {
        TrafficConfigLogicalLeafTypes + leaf.type
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkipiDropdownPreference(
                title = stringResource(Res.string.configs_rules_type),
                items = types,
                selectedIndex = types.indexOf(leaf.type).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
                onSelectedIndexChange = { onChange(leaf.copy(type = types[it])) },
            )
            IconButton(onClick = { onChange(null) }) {
                Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.common_delete), tint = MiuixTheme.colorScheme.onSurface)
            }
        }
        TextField(
            value = leaf.value,
            // Parentheses and commas are the rule syntax; they cannot be part of a value inside OR / AND.
            onValueChange = { typed -> onChange(leaf.withTypedValue(typed)) },
            label = stringResource(Res.string.configs_rules_value),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Applies typed text to a condition. Recognised input such as `keyword:google` switches the condition's type and keeps
 * only the value, like the plain rule field does.
 */
internal fun ShadowrocketCondition.Leaf.withTypedValue(typed: String): ShadowrocketCondition.Leaf {
    val intent = detectTrafficConfigRuleIntent(typed)
        ?.let { detected ->
            // Inside OR / AND a geosite reference is its own condition type; the plain field keeps it as a domain set.
            if (detected.type == "DOMAIN-SET" && detected.value.startsWith("geosite:", ignoreCase = true)) {
                TrafficConfigRuleIntent("GEOSITE", detected.value.substringAfter(':'))
            } else {
                detected
            }
        }
        ?.takeIf { it.type in TrafficConfigLogicalLeafTypes }
    // Parentheses and commas are the rule syntax; they cannot be part of a value inside OR / AND.
    fun String.clean() = filterNot { it in "()," }
    return if (intent != null) copy(type = intent.type, value = intent.value.clean()) else copy(value = typed.clean())
}
