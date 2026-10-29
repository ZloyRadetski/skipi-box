// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.R
import app.skipi.ui.routing.SkipiGeoAssetPickerDialog
import features.routing.usecase.GeoSuggestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.AppWindowDialog
import ui.text.themedFontWeight
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.loadProxyAppListPackages

@Composable
fun GeoAssetPickerDialog(
    show: Boolean,
    title: String,
    items: List<GeoSuggestionItem>,
    onSelect: (GeoSuggestionItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    SkipiGeoAssetPickerDialog(
        show = show,
        title = title,
        items = items,
        onSelect = onSelect,
        onDismissRequest = onDismissRequest,
    )
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

    AppWindowDialog(
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
                                    fontWeight = themedFontWeight(FontWeight.Medium),
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
