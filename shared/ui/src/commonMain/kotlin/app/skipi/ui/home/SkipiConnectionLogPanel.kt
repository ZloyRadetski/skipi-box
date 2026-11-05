// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ConnectionLogLevel
import app.skipi.app.home.ConnectionLogLine
import app.skipi.app.home.ConnectionLogMessageRow
import app.skipi.app.home.ConnectionLogRoute
import app.skipi.app.home.ConnectionLogRow
import app.skipi.app.home.ConnectionLogSource
import app.skipi.app.home.ConnectionLogState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_connection_log_copied
import app.skipi.ui.resources.home_connection_log_paused
import app.skipi.ui.resources.home_connection_log_paused_more
import app.skipi.ui.resources.home_connection_log_title
import app.skipi.ui.resources.home_connection_log_waiting
import app.skipi.ui.server.display.ProtocolColorUtils
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private val ConnectionLogRowHeight = 20.dp
private val ConnectionLogMessageTopPadding = 3.dp
private const val ConnectionLogCopiedMillis = 1_600L
private const val ConnectionLogProblemMaxLines = 3

/**
 * Thin live lines of what the tunnel carries and what the app and Xray log. The rows outlive the
 * session, so a failed start stays readable. The pointer over it holds the list still, so a line can
 * be read, copied or scrolled back to with the wheel; leaving returns to the newest line.
 */
@Composable
fun SkipiConnectionLogPanel(
    state: ConnectionLogState,
    connected: Boolean,
    formatTime: (Long) -> String,
    onHeldChange: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(hovered) {
        onHeldChange(hovered)
        if (!hovered) listState.scrollToItem(0)
    }
    // A keyed list stays on the row it showed; an unheld log follows the newest one instead.
    val newestRowId = state.rows.firstOrNull()?.id
    LaunchedEffect(newestRowId) {
        if (!state.held) listState.scrollToItem(0)
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(ConnectionLogCopiedMillis)
            copied = false
        }
    }

    Box(modifier = modifier.hoverable(hoverSource)) {
        if (connected || state.rows.isNotEmpty()) {
            Column {
                ConnectionLogHeader(state = state, copied = copied)
                if (state.rows.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.home_connection_log_waiting),
                        color = SkipiTheme.colors.onSurfaceVariant,
                        style = SkipiTheme.typography.labelSmall,
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(state.rows, key = ConnectionLogLine::id) { row ->
                            val onClick = {
                                onCopy(row.copyText)
                                copied = true
                            }
                            val rowModifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = tween(180),
                                fadeOutSpec = null,
                            )
                            val time = formatTime(row.timeMillis)
                            when (row) {
                                is ConnectionLogRow -> ConnectionLogLine(row, time, onClick, rowModifier)
                                is ConnectionLogMessageRow -> ConnectionLogMessageLine(row, time, onClick, rowModifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionLogHeader(state: ConnectionLogState, copied: Boolean) {
    val status = when {
        copied -> stringResource(Res.string.home_connection_log_copied)
        state.held && state.heldCount > 0 -> stringResource(Res.string.home_connection_log_paused_more, state.heldCount)
        state.held -> stringResource(Res.string.home_connection_log_paused)
        else -> ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(ConnectionLogRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.home_connection_log_title),
            color = SkipiTheme.colors.onSurfaceVariant,
            style = SkipiTheme.typography.labelSmall,
        )
        Text(
            text = status,
            color = SkipiTheme.colors.onSurfaceVariant,
            style = SkipiTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnectionLogLine(
    row: ConnectionLogRow,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = SkipiTheme.colors.onSurfaceVariant
    val style = SkipiTheme.typography.labelSmall
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ConnectionLogRowHeight)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = time, color = muted, style = style, fontFamily = FontFamily.Monospace, maxLines = 1)
        // The end of a long name is the part that says whose it is.
        Text(
            text = row.host,
            color = if (row.isAddress) muted else SkipiTheme.colors.onSurface,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.count > 1) {
            Text(text = "×${row.count}", color = muted, style = style, maxLines = 1)
        }
        Text(
            text = row.outbound,
            color = connectionLogRouteColor(row.route),
            style = style,
            fontWeight = themedFontWeight(FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = SkipiTheme.spacing.extraSmall),
        )
    }
}

@Composable
private fun ConnectionLogMessageLine(
    row: ConnectionLogMessageRow,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = SkipiTheme.colors.onSurfaceVariant
    val style = SkipiTheme.typography.labelSmall
    val isProblem = row.level == ConnectionLogLevel.Warning || row.level == ConnectionLogLevel.Error
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ConnectionLogRowHeight)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = time,
            color = muted,
            style = style,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(top = ConnectionLogMessageTopPadding),
        )
        // The reason of a failure is usually past the first line's width, so problems may wrap.
        Text(
            text = row.title,
            color = if (row.level == ConnectionLogLevel.Debug) muted else SkipiTheme.colors.onSurface,
            style = style,
            maxLines = if (isProblem) ConnectionLogProblemMaxLines else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = ConnectionLogMessageTopPadding),
        )
        if (row.count > 1) {
            Text(
                text = "×${row.count}",
                color = muted,
                style = style,
                maxLines = 1,
                modifier = Modifier.padding(top = ConnectionLogMessageTopPadding),
            )
        }
        Text(
            text = when (row.source) {
                ConnectionLogSource.App -> "SKIPI"
                ConnectionLogSource.Core -> "Xray"
            },
            color = connectionLogLevelColor(row.level),
            style = style,
            fontWeight = themedFontWeight(FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.padding(top = ConnectionLogMessageTopPadding, end = SkipiTheme.spacing.extraSmall),
        )
    }
}

@Composable
private fun connectionLogLevelColor(level: ConnectionLogLevel): Color {
    val isDark = SkipiTheme.colors.isDark
    return when (level) {
        ConnectionLogLevel.Debug, ConnectionLogLevel.Info -> SkipiTheme.colors.onSurfaceVariant
        ConnectionLogLevel.Warning -> StatusColorDefaults.pingMedium(isDark)
        ConnectionLogLevel.Error -> StatusColorDefaults.statusStopped(isDark)
    }
}

@Composable
private fun connectionLogRouteColor(route: ConnectionLogRoute): Color {
    val isDark = SkipiTheme.colors.isDark
    return when (route) {
        ConnectionLogRoute.Proxy -> ProtocolColorUtils.resolveProtocolColor(protocol = "VLESS", isDark = isDark)
        ConnectionLogRoute.Direct -> StatusColorDefaults.pingFast(isDark)
        ConnectionLogRoute.Block -> StatusColorDefaults.statusStopped(isDark)
    }
}
