// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.jetbrains.compose.resources.stringResource
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save

data class StrategyMemberSelectorItem(
    val id: Int,
    val displayTitle: String,
    val flag: String? = null,
    val subtitle: String? = null,
)

data class StrategyMemberSelectorGroup(
    val key: String,
    val title: String,
    val items: List<StrategyMemberSelectorItem>,
    val summary: ServerPickerSubscriptionSummary? = null,
)

/** Shared presentation and ordered selection behavior for strategy-group membership. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiStrategyGroupMemberSelectorScreen(
    padding: PaddingValues,
    groups: List<StrategyMemberSelectorGroup>,
    selectedServerIds: List<Int>,
    title: String,
    isWideScreen: Boolean = false,
    fallbackPainter: Painter? = null,
    flagContainerColor: Color = Color.White.copy(alpha = 0.07f),
    flagFallbackTint: Color = Color.White.copy(alpha = 0.65f),
    selectedSummary: @Composable (Int) -> String,
    emptyGroupMessage: String,
    saveLabel: String,
    onBack: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var selectedIds by remember(selectedServerIds) { mutableStateOf(selectedServerIds.distinct()) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    fun toggle(id: Int) {
        selectedIds = StrategyMemberSelectionState.toggleMember(selectedIds, id)
    }
    fun toggleGroup(group: StrategyMemberSelectorGroup) {
        selectedIds = StrategyMemberSelectionState.toggleGroup(selectedIds, group.items.map(StrategyMemberSelectorItem::id))
    }

    Scaffold(
        containerColor = SkipiTheme.colors.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                subtitle = selectedSummary(selectedIds.size),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = { NavigationIcon(onClick = { onSave(selectedIds.toList()) }, imageVector = MiuixIcons.Ok, contentDescription = saveLabel) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
            LazyColumn(state = listState, modifier = Modifier.pageScrollModifiers(scrollBehavior), contentPadding = pageListPadding(contentPadding)) {
                groups.forEach { group ->
                    val expanded = expandedGroups[group.key] ?: true
                    val ids = group.items.map(StrategyMemberSelectorItem::id)
                    val toggleState = StrategyMemberSelectionState.groupToggleState(selectedIds, ids)
                    item(key = "strategy-member-group-${group.key}") {
                        SkipiServerPickerGroupHeader(
                            title = group.title,
                            count = group.items.size,
                            summary = group.summary,
                            expanded = expanded,
                            toggleState = toggleState,
                            onToggleGroup = { toggleGroup(group) },
                            onExpandedChange = { expandedGroups[group.key] = it },
                        )
                    }
                    if (expanded) {
                        if (group.items.isEmpty()) {
                            item(key = "strategy-member-empty-${group.key}") { SkipiServerPickerEmptyGroupRow(emptyGroupMessage) }
                        } else {
                            val lastId = group.items.last().id
                            items(group.items, key = { "strategy-member-server-${group.key}-${it.id}" }, contentType = { "strategy-member-server" }) { server ->
                                SkipiServerPickerItemRow(
                                    flag = server.flag,
                                    displayTitle = server.displayTitle,
                                    subtitle = server.subtitle,
                                    selected = server.id in selectedIds,
                                    isLast = server.id == lastId,
                                    fallbackPainter = fallbackPainter,
                                    flagContainerColor = flagContainerColor,
                                    flagFallbackTint = flagFallbackTint,
                                    onClick = { toggle(server.id) },
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollBar(adapter = rememberScrollBarAdapter(listState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), trackPadding = contentPadding)
        }
    }
}
