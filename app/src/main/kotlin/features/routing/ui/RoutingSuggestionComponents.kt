// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.R
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.loadProxyAppListPackages
import features.routing.usecase.GeoSuggestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme

@Composable
fun GeoAssetPickerDialog(
    show: Boolean,
    title: String,
    items: List<GeoSuggestionItem>,
    onSelect: (GeoSuggestionItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var searchQuery by remember(show) { mutableStateOf("") }
    var selectedGroup by remember(show) { mutableStateOf<String?>(null) }

    val distinctGroups = remember(items) {
        items.map { it.groupName }.distinct()
    }

    val filteredItems = remember(items, searchQuery, selectedGroup) {
        items.filter { item ->
            val matchesGroup = selectedGroup == null || item.groupName == selectedGroup
            val matchesSearch = searchQuery.isBlank() ||
                item.tag.contains(searchQuery.trim(), ignoreCase = true) ||
                item.fullRule.contains(searchQuery.trim(), ignoreCase = true) ||
                item.groupName.contains(searchQuery.trim(), ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }

    WindowDialog(
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
                state = rememberTextFieldState(initialText = searchQuery),
                inputTransformation = { searchQuery = asCharSequence().toString() },
                label = stringResource(R.string.routing_suggestions_search_hint),
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
                        text = stringResource(R.string.proxy_server_list_all),
                        selected = selectedGroup == null,
                        onClick = { selectedGroup = null },
                    )
                    distinctGroups.forEach { group ->
                        RoutingFilterChip(
                            text = group,
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
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
                        text = stringResource(R.string.routing_suggestions_no_results),
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
                                onSelect(item)
                                onDismissRequest()
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
                    text = stringResource(R.string.common_close),
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
            .background(AppTheme.colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.tag,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
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
                    .background(AppTheme.colors.accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = item.groupName,
                    fontSize = 11.sp,
                    color = AppTheme.colors.onAccent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ProcessAppPickerDialog(
    show: Boolean,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val packageCatalog = LocalAppServices.current.packageCatalog
    var apps by remember { mutableStateOf<List<AppPackageEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember(show) { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(show) {
        if (show) {
            loading = true
            apps = withContext(Dispatchers.IO) {
                runCatching {
                    packageCatalog.loadProxyAppListPackages(
                        showSystemApps = false,
                        currentUserOnly = true,
                        excludedPackageName = context.packageName,
                    )
                }.getOrDefault(emptyList())
            }
            loading = false
        }
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else {
            val query = searchQuery.trim().lowercase()
            apps.filter { app ->
                app.packageName.lowercase().contains(query) ||
                    (app.appLabel?.lowercase()?.contains(query) == true)
            }
        }
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.routing_suggestions_select_app),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            TextField(
                state = rememberTextFieldState(initialText = searchQuery),
                inputTransformation = { searchQuery = asCharSequence().toString() },
                label = stringResource(R.string.proxy_app_list_search_label),
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

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.proxy_app_list_loading),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.routing_suggestions_no_results),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                        items = filteredApps,
                        key = { it.packageName },
                    ) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .clickable {
                                    onSelect(app.packageName)
                                    onDismissRequest()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appLabel ?: app.packageName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.common_close),
                    onClick = onDismissRequest,
                )
            }
        }
    }
}

@Composable
fun SuggestionChipsRow(
    chips: List<String>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    selectedChip: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actionButtonText != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.colors.accent)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.onAccent,
                )
            }
        }

        chips.forEach { chip ->
            val isSelected = selectedChip == chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) AppTheme.colors.accent
                        else AppTheme.colors.surfaceVariant,
                    )
                    .clickable { onChipClick(chip) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = chip,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) AppTheme.colors.onAccent else MiuixTheme.colorScheme.onSurface,
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
            .background(
                if (selected) AppTheme.colors.accent
                else AppTheme.colors.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppTheme.colors.onAccent else MiuixTheme.colorScheme.onSurface,
        )
    }
}
