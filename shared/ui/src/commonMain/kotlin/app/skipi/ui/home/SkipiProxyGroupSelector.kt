// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SkipiProxyGroupItem(
    val id: String,
    val title: String,
    val serverCount: Int,
    val enabled: Boolean,
)

data class SkipiProxyGroupSelectorColors(
    val surface: Color,
    val raisedSurface: Color,
    val selectedSurface: Color,
    val border: Color,
    val selectedBorder: Color,
    val text: Color,
    val mutedText: Color,
)

/** Common horizontally-scrollable proxy-group selector for the shared Home UI. */
@Composable
fun SkipiProxyGroupSelector(
    groups: List<SkipiProxyGroupItem>,
    selectedGroupId: String?,
    title: String,
    emptyText: String,
    disabledTitle: (String) -> String,
    serverCountText: (Int) -> String,
    colors: SkipiProxyGroupSelectorColors,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (groups.isEmpty()) {
                Text(emptyText, color = colors.mutedText, fontSize = 14.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { group ->
                        val selected = group.id == selectedGroupId
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { onSelect(group.id) },
                            color = if (selected) colors.selectedSurface else colors.raisedSurface,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) colors.selectedBorder else Color.Transparent,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                                Text(
                                    text = if (group.enabled) group.title else disabledTitle(group.title),
                                    color = if (group.enabled) colors.text else colors.mutedText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = serverCountText(group.serverCount),
                                    color = colors.mutedText,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
