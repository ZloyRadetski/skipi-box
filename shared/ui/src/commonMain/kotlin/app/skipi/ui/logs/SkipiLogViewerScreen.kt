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
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.conflate
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import app.skipi.ui.resources.logs_nothing_found
import app.skipi.ui.resources.logs_search_hint
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppPullToRefresh
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copy
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBarDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
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
    var query by remember { mutableStateOf("") }
    val search = remember(query) { LogSearch.parse(query) }
    // Oldest first, like a terminal: the newest line is at the bottom, where the log opens.
    // A search goes over up to several thousand lines, building each one's text: it runs off the UI thread, a little
    // after the last keystroke, and the list keeps showing the previous result meanwhile.
    // Only the query is debounced: live entries must reach the filter without waiting, or a busy log (a line
    // every few milliseconds) would restart the wait forever and the search would never show anything.
    var debouncedSearch by remember { mutableStateOf(search) }
    LaunchedEffect(search) {
        if (!search.isEmpty) delay(LogSearchDebounceMillis)
        debouncedSearch = search
    }
    var filtered by remember { mutableStateOf<List<CoreLogEntry>?>(null) }
    val latestEntries by rememberUpdatedState(entries)
    val latestShowMetadata by rememberUpdatedState(showMetadata)
    LaunchedEffect(Unit) {
        // conflate: a pass that finishes always publishes; passes that pile up behind it collapse into one.
        snapshotFlow { Triple(latestEntries, debouncedSearch, latestShowMetadata) }.conflate().collect { (all, query, metadata) ->
            filtered = if (query.isEmpty) {
                null
            } else {
                withContext(Dispatchers.Default) { all.filter { query.matches(it, it.exportText(metadata)) } }
            }
        }
    }
    val displayed = if (search.isEmpty) entries else filtered ?: entries
    val copiedMessage = stringResource(Res.string.logs_copied_to_clipboard)
    val hapticFeedback = LocalHapticFeedback.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                subtitle = stringResource(Res.string.logs_count).formatTemplate("count" to displayed.size),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    if (onExport != null) {
                        NavigationIcon(
                            onClick = { onExport(displayed.toList(), title) },
                            imageVector = MiuixIcons.Share,
                            contentDescription = stringResource(Res.string.logs_export),
                        )
                    }
                    NavigationIcon(
                        onClick = { onCopy(logBlockText(displayed, showMetadata), copiedMessage) },
                        imageVector = MiuixIcons.Copy,
                        contentDescription = stringResource(Res.string.common_copy),
                    )
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
        // A reader who stays at the bottom keeps following the newest lines; one who scrolled up is left alone.
        var following by remember { mutableStateOf(true) }
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.isScrollInProgress }
                .filter { scrolling -> !scrolling }
                .collect { following = !lazyListState.canScrollForward }
        }
        var previousNewestId by remember { mutableStateOf<Long?>(null) }
        var previousQuery by remember { mutableStateOf(query) }
        val newestId = displayed.lastOrNull()?.id
        LaunchedEffect(newestId, displayed.size, query) {
            val target = logScrollTarget(previousNewestId, newestId, displayed.size, following, listChanged = query != previousQuery)
            previousNewestId = newestId
            previousQuery = query
            if (target != null) {
                lazyListState.scrollToItem(target)
                following = true
            }
        }
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = PaddingValues(bottom = 12.dp)
        Box {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                SmallTitle(text = stringResource(Res.string.logs_output))
                LogSearchField(query = query, onQueryChange = { query = it })
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
                        SelectionContainer {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.pageScrollModifiers(scrollBehavior),
                            contentPadding = listPadding,
                        ) {
                            if (displayed.isEmpty()) {
                                item(key = "log_empty") { LogEmptyText(searching = !search.isEmpty) }
                            } else {
                                items(items = displayed, key = { it.id }) { entry ->
                                    val line = remember(entry, showMetadata) { entry.logLine(showMetadata) }
                                    LogTextLine(line, search)
                                }
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
private fun LogEmptyText(searching: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (searching) Res.string.logs_nothing_found else Res.string.common_empty),
            style = MiuixTheme.textStyles.body2,
            color = SkipiTheme.colors.onSurfaceVariant,
        )
    }
}

/** One entry as plain monospace text: dim time, tinted level, wrapped message; no background. */
@Composable
private fun LogTextLine(line: LogLine, search: LogSearch) {
    val dim = SkipiTheme.colors.onSurfaceVariant
    val body = SkipiTheme.colors.onSurface
    val levelColor = when (logTone(line.level.orEmpty())) {
        LogTone.Error -> SkipiTheme.colors.error
        LogTone.Warning -> SkipiTheme.colors.warning
        LogTone.Neutral -> dim
    }
    val mark = SkipiTheme.colors.accent.copy(alpha = 0.35f)
    val text = remember(line, search, dim, body, levelColor, mark) {
        buildAnnotatedString {
            if (line.time != null && line.level != null) {
                withStyle(SpanStyle(color = dim)) { append(line.time) }
                append("  ")
                withStyle(SpanStyle(color = levelColor)) { append(line.level) }
                append("  ")
            }
            withStyle(SpanStyle(color = body)) { append(line.message) }
            // Offsets are those of the plain line, which is what the text above spells out.
            for (range in search.highlightRanges(line.plain)) {
                if (range.last < length) addStyle(SpanStyle(background = mark), range.first, range.last + 1)
            }
        }
    }
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
        // A single multi-KB core line must not become an endless list item; copy keeps the full text.
        maxLines = 12,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** One-line search box above the log: no card, a hairline-free field on the surface colour. */
@Composable
private fun LogSearchField(query: String, onQueryChange: (String) -> Unit) {
    val state = remember { TextFieldState(query) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { onQueryChange(it) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SkipiTheme.colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (state.text.isEmpty()) {
            Text(
                text = stringResource(Res.string.logs_search_hint),
                style = MiuixTheme.textStyles.body2,
                color = SkipiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            state = state,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MiuixTheme.textStyles.body2.copy(color = SkipiTheme.colors.onSurface),
            cursorBrush = SolidColor(SkipiTheme.colors.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How long a search waits after the last keystroke before it filters the lines. */
private const val LogSearchDebounceMillis = 150L
