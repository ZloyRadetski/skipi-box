// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.subscription_group_list
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

/** Shared subscription-group list body. The host owns navigation and subscription refresh effects. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SubscriptionGroupListContent(
    groups: List<SubscriptionGroupUiState>,
    updatingGroupIds: Set<Int>,
    contentPadding: PaddingValues,
    onToggle: (SubscriptionGroupUiState, Boolean) -> Unit,
    onUpdate: (SubscriptionGroupUiState) -> Unit,
    onEdit: (SubscriptionGroupUiState) -> Unit,
    onDelete: (SubscriptionGroupUiState) -> Unit,
    modifier: Modifier = Modifier,
    scrollModifier: Modifier = Modifier,
    trackPadding: PaddingValues = contentPadding,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = scrollModifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "subscription_title") {
                SmallTitle(text = stringResource(Res.string.subscription_group_list))
            }
            items(groups, key = { it.id }) { group ->
                SubscriptionGroupCard(
                    group = group,
                    isUpdating = group.id in updatingGroupIds,
                    onToggle = { enabled -> onToggle(group, enabled) },
                    onUpdate = if (group.url.isNotBlank()) ({ onUpdate(group) }) else null,
                    onEdit = { onEdit(group) },
                    onDelete = { onDelete(group) },
                )
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = trackPadding,
        )
    }
}
