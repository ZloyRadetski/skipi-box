// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.skipi.ui.components.AppWindowDropdownPreference
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
import features.config.toStoredBodyText
import features.config.xrayUnsupportedReasonForCondition
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
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
 */
@Composable
fun SkipiTrafficConfigLogicalConditionEditor(
    operator: ShadowrocketLogicalOperator,
    bodyText: String,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var children by remember {
        mutableStateOf(parseShadowrocketConditionBody(operator, bodyText)?.children.orEmpty())
    }
    val root = ShadowrocketCondition.Group(operator, children)
    val unsupported = xrayUnsupportedReasonForCondition(operator, root) != null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupContent(
            group = root,
            depth = 1,
            onChange = { updated ->
                children = updated.children
                onBodyChange(updated.toStoredBodyText())
            },
        )
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

@Composable
private fun GroupContent(
    group: ShadowrocketCondition.Group,
    depth: Int,
    onChange: (ShadowrocketCondition.Group) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.children.forEachIndexed { index, child ->
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(stringResource(Res.string.configs_rules_add_condition)) {
                onChange(group.copy(children = group.children + ShadowrocketCondition.Leaf("DOMAIN-SUFFIX", "")))
            }
            if (depth < MaxEditorGroupDepth) {
                ActionChip(stringResource(Res.string.configs_rules_add_group)) {
                    val nested = ShadowrocketCondition.Group(
                        ShadowrocketLogicalOperator.And,
                        listOf(ShadowrocketCondition.Leaf("DOMAIN-SUFFIX", "")),
                    )
                    onChange(group.copy(children = group.children + nested))
                }
            }
        }
    }
}

@Composable
private fun NestedGroup(
    group: ShadowrocketCondition.Group,
    depth: Int,
    onChange: (ShadowrocketCondition?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppWindowDropdownPreference(
                title = stringResource(Res.string.configs_rules_group_operator),
                items = LogicalOperators.map { it.name.uppercase() },
                selectedIndex = LogicalOperators.indexOf(group.operator).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
                onSelectedIndexChange = { onChange(group.copy(operator = LogicalOperators[it])) },
            )
            IconButton(onClick = { onChange(null) }) {
                Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.common_delete), tint = MiuixTheme.colorScheme.onSurface)
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            GroupContent(group, depth + 1, onChange = { onChange(it) })
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
            AppWindowDropdownPreference(
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
            onValueChange = { typed -> onChange(leaf.copy(value = typed.filterNot { it in "()," })) },
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
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
