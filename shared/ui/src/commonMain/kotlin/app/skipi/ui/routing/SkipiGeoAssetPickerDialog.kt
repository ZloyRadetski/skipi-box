// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_close
import app.skipi.ui.resources.proxy_server_list_all
import app.skipi.ui.resources.routing_suggestions_no_results
import app.skipi.ui.resources.routing_suggestions_search_hint
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import features.routing.usecase.GeoSuggestionItem
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SkipiGeoAssetPickerDialog(
    show: Boolean,
    title: String,
    items: List<GeoSuggestionItem>,
    onSelect: (GeoSuggestionItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var filterState by remember(show) { mutableStateOf(GeoAssetPickerFilterState.forShow(show)) }
    val distinctGroups = remember(items) { geoSuggestionGroups(items) }
    val filteredItems = remember(items, filterState) { filterGeoSuggestionItems(items, filterState) }

    AppWindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            TextField(
                state = rememberTextFieldState(initialText = filterState.searchQuery),
                inputTransformation = { filterState = filterState.copy(searchQuery = asCharSequence().toString()) },
                label = stringResource(Res.string.routing_suggestions_search_hint),
                lineLimits = TextFieldLineLimits.SingleLine,
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            if (distinctGroups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoutingFilterChip(
                        text = stringResource(Res.string.proxy_server_list_all),
                        selected = filterState.selectedGroup == null,
                        onClick = { filterState = filterState.copy(selectedGroup = null) },
                    )
                    distinctGroups.forEach { group ->
                        RoutingFilterChip(
                            text = group,
                            selected = filterState.selectedGroup == group,
                            onClick = { filterState = filterState.copy(selectedGroup = group) },
                        )
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.routing_suggestions_no_results),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = filteredItems,
                        key = { "${it.groupName}:${it.fullRule}" },
                    ) { item ->
                        GeoSuggestionItemRow(
                            item = item,
                            onClick = {
                                selectGeoSuggestionThenDismiss(item, onSelect, onDismissRequest)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(Res.string.common_close),
                    onClick = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun GeoSuggestionItemRow(
    item: GeoSuggestionItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SkipiTheme.colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.tag,
                fontSize = 15.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.fullRule,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isCustom) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SkipiTheme.colors.accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = item.groupName,
                    fontSize = 11.sp,
                    color = SkipiTheme.colors.onAccent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RoutingFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SkipiTheme.colors.accent else SkipiTheme.colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SkipiTheme.colors.onAccent else MiuixTheme.colorScheme.onSurface,
        )
    }
}
