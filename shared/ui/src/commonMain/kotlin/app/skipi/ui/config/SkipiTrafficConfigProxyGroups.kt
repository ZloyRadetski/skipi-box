// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import features.config.ShadowrocketPolicyGroup
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared presentation for Shadowrocket profile proxy groups; the host supplies labels and actions. */
@Composable
fun SkipiTrafficConfigProxyGroups(
    groups: List<ShadowrocketPolicyGroup>,
    contentPadding: PaddingValues,
    summary: String,
    addLabel: String,
    emptyLabel: String,
    editContentDescription: String,
    deleteContentDescription: String,
    onAddGroup: () -> Unit,
    onEditGroup: (ShadowrocketPolicyGroup) -> Unit,
    onDeleteGroup: (ShadowrocketPolicyGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "description") {
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )
        }
        item(key = "add") {
            TextButton(
                text = addLabel,
                onClick = onAddGroup,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = emptyLabel,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
        }
        items(items = groups, key = ShadowrocketPolicyGroup::lineNumber) { group ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditGroup(group) },
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                colors = CardDefaults.defaultColors(
                    color = SkipiTheme.colors.surface,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.name, style = MiuixTheme.textStyles.title3)
                        Text(
                            text = "${group.type}: ${group.members.joinToString()}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onEditGroup(group) }) {
                        Icon(MiuixIcons.Edit, contentDescription = editContentDescription)
                    }
                    IconButton(onClick = { onDeleteGroup(group) }) {
                        Icon(MiuixIcons.Delete, contentDescription = deleteContentDescription)
                    }
                }
            }
        }
    }
}
