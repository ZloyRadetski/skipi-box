// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class GroupMemberChoice(
    val id: Int,
    val displayName: String,
    val protocol: String,
    val flag: String? = null,
    val latency: String = "",
    val selected: Boolean = false,
)

@Composable
fun SkipiSelectGroupMemberDialog(
    show: Boolean,
    title: String,
    summary: String,
    members: List<GroupMemberChoice>,
    noMembersMessage: String,
    cancelLabel: String,
    onDismissRequest: () -> Unit,
    onSelectMember: (Int) -> Unit,
) {
    if (!show) return
    AppWindowDialog(show = true, title = title, onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 12.dp))
            if (members.isEmpty()) {
                Text(noMembersMessage, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
                ) {
                    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(members, key = GroupMemberChoice::id) { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectMember(member.id); onDismissRequest() }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = member.selected, onClick = { onSelectMember(member.id); onDismissRequest() })
                                Spacer(Modifier.width(12.dp))
                                if (member.flag != null) Text(text = member.flag, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(member.displayName, style = MiuixTheme.textStyles.body1, fontWeight = if (member.selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(member.protocol, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                if (member.latency.isNotBlank()) Text(member.latency, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(text = cancelLabel, onClick = onDismissRequest)
            }
        }
    }
}
