// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_empty
import app.skipi.ui.resources.proxy_app_list_search_label
import features.proxy.app.model.ProxyAppListUserSpaceTabUi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Search input shared by Android's PackageManager-backed list host. */
@Composable
fun SkipiTrafficConfigProxyAppListSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onDebouncedQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDebouncedQueryChange by rememberUpdatedState(onDebouncedQueryChange)
    LaunchedEffect(query) {
        delay(150L)
        currentOnDebouncedQueryChange(query)
    }

    SearchBar(
        modifier = modifier,
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = stringResource(Res.string.proxy_app_list_search_label),
            )
        },
        expanded = false,
        onExpandedChange = {},
    ) {}
}

/** Displays user tabs only when there is a choice to make. */
@Composable
fun SkipiTrafficConfigProxyAppListUserTabs(
    tabs: List<ProxyAppListUserSpaceTabUi>,
    selectedUserId: Int?,
    onSelectedUserIdChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size <= 1) return

    val hapticFeedback = LocalHapticFeedback.current
    val selectedIndex = tabs.indexOfFirst { tab -> tab.id == selectedUserId }
        .coerceAtLeast(0)
    TabRowWithContour(
        tabs = tabs.map { tab -> "${tab.label} (${tab.checkedCount})" },
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            onSelectedUserIdChange(tabs[index].id)
        },
        colors = TabRowDefaults.tabRowColors(
            selectedBackgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedContentColor = MiuixTheme.colorScheme.primary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        maxWidth = 132.dp,
    )
}

@Composable
internal fun ProxyAppListEmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.common_empty),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
