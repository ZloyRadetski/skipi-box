// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package app.skipi.ui.logs

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppPullToRefresh
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_empty
import app.skipi.ui.resources.logs_copied_to_clipboard
import app.skipi.ui.resources.logs_count
import app.skipi.ui.resources.logs_export
import app.skipi.ui.resources.logs_output
import app.skipi.ui.resources.logs_pull_to_refresh
import app.skipi.ui.resources.logs_refreshed
import app.skipi.ui.resources.logs_refreshing
import app.skipi.ui.resources.logs_release_to_refresh
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.theme.SkipiTheme
import features.logs.CoreLogEntry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBarDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Text of one log entry as copied to the clipboard and written by an export. */
fun CoreLogEntry.exportText(showMetadata: Boolean): String =
    if (showMetadata) "$time  ${level.uppercase()}  $message" else message

/**
 * Log viewer shared by the core, access and application log pages. The host owns the data
 * source: it passes the latest [entries] (newest last), refreshes its repository in [onRefresh],
 * and performs the platform actions (clear, export to a file, clipboard).
 */
@Composable
fun SkipiLogViewerScreen(
    title: String,
    entries: List<CoreLogEntry>,
    showMetadata: Boolean,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onRefresh: suspend () -> Unit,
    onClear: suspend () -> Unit,
    onExport: ((entries: List<CoreLogEntry>, title: String) -> Unit)?,
    onCopy: (text: String, confirmation: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var refreshing by remember { mutableStateOf(false) }
    val displayed = remember(entries) { entries.asReversed() }
    val copiedMessage = stringResource(Res.string.logs_copied_to_clipboard)
    val hapticFeedback = LocalHapticFeedback.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                subtitle = stringResource(Res.string.logs_count).formatTemplate("count" to entries.size),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    if (onExport != null) {
                        NavigationIcon(
                            onClick = { onExport(entries.toList(), title) },
                            imageVector = MiuixIcons.Share,
                            contentDescription = stringResource(Res.string.logs_export),
                        )
                    }
                    NavigationIcon(
                        onClick = { scope.launch { onClear() } },
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(Res.string.common_delete),
                    )
                },
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = PaddingValues(bottom = 12.dp)
        Box {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                SmallTitle(text = stringResource(Res.string.logs_output))
                Box(modifier = Modifier.weight(1f)) {
                    AppPullToRefresh(
                        isRefreshing = refreshing,
                        onRefresh = {
                            if (!refreshing) {
                                scope.launch {
                                    refreshing = true
                                    withFrameNanos { }
                                    try {
                                        onRefresh()
                                    } finally {
                                        withFrameNanos { }
                                        refreshing = false
                                    }
                                }
                            }
                        },
                        hapticFeedback = hapticFeedback,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listPadding,
                        topAppBarScrollBehavior = scrollBehavior,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        refreshTexts = listOf(
                            stringResource(Res.string.logs_pull_to_refresh),
                            stringResource(Res.string.logs_release_to_refresh),
                            stringResource(Res.string.logs_refreshing),
                            stringResource(Res.string.logs_refreshed),
                        ),
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.pageScrollModifiers(scrollBehavior),
                            contentPadding = listPadding,
                        ) {
                            if (displayed.isEmpty()) {
                                item(key = "log_empty") { LogEmptyCard() }
                            } else {
                                items(items = displayed, key = { it.id }) { entry ->
                                    LogEntryCard(
                                        text = entry.exportText(showMetadata),
                                        onClick = { onCopy(entry.exportText(showMetadata), copiedMessage) },
                                    )
                                }
                            }
                        }
                    }
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(lazyListState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        trackPadding = listPadding,
                        thumbWidth = 5.dp,
                        thumbMinLength = 48.dp,
                        colors = ScrollBarDefaults.scrollBarColors(
                            thumbColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEmptyCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.common_empty),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun LogEntryCard(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
        colors = CardDefaults.defaultColors(
            color = SkipiTheme.colors.surface,
            contentColor = SkipiTheme.colors.onSurface,
        ),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            // A single multi-KB core line must not become an endless list item; copy keeps the full text.
            maxLines = 8,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
