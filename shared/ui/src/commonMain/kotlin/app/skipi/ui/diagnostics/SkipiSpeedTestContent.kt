// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.tools_speed_failed
import app.skipi.ui.resources.tools_speed_mbps
import app.skipi.ui.resources.tools_speed_ms
import app.skipi.ui.resources.tools_speed_phase_download
import app.skipi.ui.resources.tools_speed_phase_failed
import app.skipi.ui.resources.tools_speed_phase_finished
import app.skipi.ui.resources.tools_speed_phase_idle
import app.skipi.ui.resources.tools_speed_phase_ping
import app.skipi.ui.resources.tools_speed_phase_upload
import app.skipi.ui.resources.tools_speed_result_download
import app.skipi.ui.resources.tools_speed_result_jitter
import app.skipi.ui.resources.tools_speed_result_ping
import app.skipi.ui.resources.tools_speed_result_upload
import app.skipi.ui.resources.tools_speed_start
import app.skipi.ui.resources.tools_speed_stop
import app.skipi.ui.settings.readableOn
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class SpeedTestUiPhase { Idle, Ping, Download, Upload, Finished, Failed }

data class SpeedTestUiResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

/** At least this wide, the dial and the results sit side by side. */
private val WideLayoutWidth = 760.dp
private const val MaxHistory = 90

/**
 * The speed test in the app's own look: Miuix cards, the user's accent color, no decoration of its own. A dial with
 * the live speed and a track per stage, the speed over time, and the four results. On a wide window the dial and the
 * results sit side by side, on a phone they stack. [downloadSamples] and [uploadSamples] replace the history the
 * screen records itself; previews and tests use them.
 */
@Composable
fun SkipiSpeedTestContent(
    phase: SpeedTestUiPhase,
    progress: Float,
    currentMbps: Double?,
    result: SpeedTestUiResult?,
    errorMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    downloadSamples: List<Float>? = null,
    uploadSamples: List<Float>? = null,
) {
    val history = rememberSpeedHistory(phase, currentMbps)
    val down = downloadSamples ?: history.down
    val up = uploadSamples ?: history.up
    val running = phase == SpeedTestUiPhase.Ping || phase == SpeedTestUiPhase.Download || phase == SpeedTestUiPhase.Upload
    // The dial shows the live speed while a transfer runs and the download result once the test is done.
    val shown = when (phase) {
        SpeedTestUiPhase.Download, SpeedTestUiPhase.Upload -> currentMbps ?: 0.0
        SpeedTestUiPhase.Finished -> result?.downloadMbps ?: 0.0
        else -> 0.0
    }
    BoxWithConstraints(modifier = modifier) {
        val dial: @Composable (Modifier) -> Unit = { dialModifier ->
            DialCard(
                phase = phase,
                progress = progress,
                running = running,
                shownMbps = shown,
                errorMessage = errorMessage,
                onStart = onStart,
                onStop = onStop,
                modifier = dialModifier,
            )
        }
        val details: @Composable (Modifier) -> Unit = { detailsModifier ->
            Column(detailsModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GraphCard(down = down, up = up)
                ResultTiles(result = result)
            }
        }
        if (maxWidth >= WideLayoutWidth) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                dial(Modifier.weight(1f))
                details(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dial(Modifier.fillMaxWidth())
                details(Modifier.fillMaxWidth())
            }
        }
    }
}

private class SpeedHistory {
    val down = mutableStateListOf<Float>()
    val up = mutableStateListOf<Float>()
}

/** Remembers the speeds of the run on screen; a new run starts a clean curve. */
@Composable
private fun rememberSpeedHistory(phase: SpeedTestUiPhase, currentMbps: Double?): SpeedHistory {
    val history = remember { SpeedHistory() }
    LaunchedEffect(phase) {
        if (phase == SpeedTestUiPhase.Ping || phase == SpeedTestUiPhase.Idle) {
            history.down.clear()
            history.up.clear()
        }
    }
    LaunchedEffect(phase, currentMbps) {
        val value = currentMbps?.toFloat() ?: return@LaunchedEffect
        val target = when (phase) {
            SpeedTestUiPhase.Download -> history.down
            SpeedTestUiPhase.Upload -> history.up
            else -> return@LaunchedEffect
        }
        target.add(value)
        if (target.size > MaxHistory) target.removeAt(0)
    }
    return history
}

/** The speed shown with the digits that matter at its size. */
private fun formatSpeed(value: Float): String = when {
    value >= 100f -> "%.0f".format(value)
    value >= 10f -> "%.1f".format(value)
    else -> "%.2f".format(value)
}

// ---------------------------------------------------------------------------------------------------------------
// The dial

@Composable
private fun DialCard(
    phase: SpeedTestUiPhase,
    progress: Float,
    running: Boolean,
    shownMbps: Double,
    errorMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    when (phase) {
                        SpeedTestUiPhase.Idle -> Res.string.tools_speed_phase_idle
                        SpeedTestUiPhase.Ping -> Res.string.tools_speed_phase_ping
                        SpeedTestUiPhase.Download -> Res.string.tools_speed_phase_download
                        SpeedTestUiPhase.Upload -> Res.string.tools_speed_phase_upload
                        SpeedTestUiPhase.Finished -> Res.string.tools_speed_phase_finished
                        SpeedTestUiPhase.Failed -> Res.string.tools_speed_phase_failed
                    },
                ),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(18.dp))
            SpeedDial(mbps = shownMbps, phase = phase, modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth())
            if (phase == SpeedTestUiPhase.Failed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.tools_speed_failed, errorMessage.orEmpty()),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            StageTracks(phase = phase, progress = progress, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (running) onStop() else onStart() },
                colors = if (running) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (running) Res.string.tools_speed_stop else Res.string.tools_speed_start),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (running) MiuixTheme.colorScheme.onSurface else readableOn(MiuixTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun SpeedDial(mbps: Double, phase: SpeedTestUiPhase, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val accent = SkipiTheme.colors.accent
    val fraction by animateFloatAsState(speedGaugeFraction(mbps), tween(600, easing = FastOutSlowInEasing), label = "dial")
    val number by animateFloatAsState(mbps.toFloat(), tween(400), label = "dialNumber")
    val track = colors.onSurface.copy(alpha = 0.08f)
    val markDim = colors.onSurface.copy(alpha = 0.28f)
    val markLit = colors.onSurface.copy(alpha = 0.70f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp)
    val labelled = remember { setOf(0, 2, 4, 6, 8) }
    val idle = phase == SpeedTestUiPhase.Idle || phase == SpeedTestUiPhase.Ping
    val uploading = phase == SpeedTestUiPhase.Upload
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val side = minOf(maxWidth, 340.dp)
        // The 270 degree dial leaves its lower quarter empty: the box is cut there so what follows sits closer.
        Box(Modifier.size(width = side, height = side * 0.88f), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.requiredSize(side), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(side)) {
                    val w = size.minDimension
                    val stroke = w * 0.06f
                    val center = Offset(w / 2f, w / 2f)
                    val radius = w / 2f - stroke
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val arc = Size(radius * 2f, radius * 2f)
                    val startAngle = 135f
                    val sweepTotal = 270f
                    drawArc(track, startAngle, sweepTotal, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    val sweep = sweepTotal * fraction
                    if (sweep > 0.5f) {
                        drawArc(accent, startAngle, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    val markRadius = radius - stroke * 1.6f
                    SpeedGaugeMarks.forEachIndexed { index, mark ->
                        val at = index.toFloat() / SpeedGaugeMarks.lastIndex
                        val angle = Math.toRadians((startAngle + sweepTotal * at).toDouble())
                        val lit = at <= fraction + 0.001f
                        if (index in labelled) {
                            val text = if (index == SpeedGaugeMarks.lastIndex) "$mark+" else mark.toString()
                            val layout = measurer.measure(text, labelStyle)
                            val labelRadius = markRadius - layout.size.width * 0.3f
                            drawText(
                                layout,
                                color = if (lit) markLit else markDim,
                                topLeft = Offset(
                                    center.x + labelRadius * cos(angle).toFloat() - layout.size.width / 2f,
                                    center.y + labelRadius * sin(angle).toFloat() - layout.size.height / 2f,
                                ),
                            )
                        } else {
                            drawCircle(
                                if (lit) markLit else markDim,
                                radius = stroke * 0.09f,
                                center = Offset(center.x + markRadius * cos(angle).toFloat(), center.y + markRadius * sin(angle).toFloat()),
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (idle) "--" else formatSpeed(number),
                        fontSize = (side.value * 0.17f).sp,
                        fontWeight = FontWeight.Light,
                        color = colors.onSurface,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!idle || phase == SpeedTestUiPhase.Finished) {
                            Icon(
                                imageVector = if (uploading) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = stringResource(Res.string.tools_speed_mbps),
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Stages

private fun SpeedTestUiPhase.stageIndex(): Int = when (this) {
    SpeedTestUiPhase.Idle, SpeedTestUiPhase.Failed -> -1
    SpeedTestUiPhase.Ping -> 0
    SpeedTestUiPhase.Download -> 1
    SpeedTestUiPhase.Upload -> 2
    SpeedTestUiPhase.Finished -> 3
}

/** Ping, download and upload as three tracks; a finished stage is full, the running one follows [progress]. */
@Composable
private fun StageTracks(phase: SpeedTestUiPhase, progress: Float, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val accent = SkipiTheme.colors.accent
    val current = phase.stageIndex()
    val labels = listOf(Res.string.tools_speed_result_ping, Res.string.tools_speed_result_download, Res.string.tools_speed_result_upload)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val fill by animateFloatAsState(
                when {
                    index < current -> 1f
                    index == current -> progress.coerceIn(0f, 1f)
                    else -> 0f
                },
                tween(300),
                label = "stage$index",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(fill).height(4.dp).clip(CircleShape).background(accent))
                }
                Text(
                    text = stringResource(label),
                    fontSize = 11.sp,
                    color = if (index <= current) colors.onSurface else colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The curve

@Composable
private fun GraphCard(down: List<Float>, up: List<Float>) {
    val colors = MiuixTheme.colorScheme
    val accent = SkipiTheme.colors.accent
    val neutral = colors.onSurfaceVariantSummary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(accent, stringResource(Res.string.tools_speed_result_download))
                LegendDot(neutral, stringResource(Res.string.tools_speed_result_upload))
            }
            Spacer(Modifier.height(10.dp))
            val grid = colors.onSurface.copy(alpha = 0.06f)
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val maxValue = max(10f, max(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f)) * 1.15f
                for (line in 0..3) {
                    val y = size.height * line / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawSeries(down, maxValue, accent, filled = true)
                drawSeries(up, maxValue, neutral, filled = false)
            }
        }
    }
}

private fun DrawScope.drawSeries(values: List<Float>, maxValue: Float, color: Color, filled: Boolean) {
    if (values.size < 2) return
    val step = size.width / (values.size - 1)
    val points = values.mapIndexed { index, value ->
        Offset(index * step, size.height - size.height * (value / maxValue).coerceIn(0f, 1f))
    }
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val next = points[i]
            val mid = (previous.x + next.x) / 2f
            cubicTo(mid, previous.y, mid, next.y, next.x, next.y)
        }
    }
    if (filled) {
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
    }
    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The results

@Composable
private fun ResultTiles(result: SpeedTestUiResult?) {
    val mbps = stringResource(Res.string.tools_speed_mbps)
    val ms = stringResource(Res.string.tools_speed_ms)
    val dark = SkipiTheme.colors.isDark
    // Latency reads like the server list: green when fast, amber and orange as it grows.
    fun latencyColor(value: Double?): Color? = when {
        value == null -> null
        value < 100.0 -> StatusColorDefaults.pingFast(dark)
        value < 200.0 -> StatusColorDefaults.pingMedium(dark)
        else -> StatusColorDefaults.pingSlow(dark)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(stringResource(Res.string.tools_speed_result_download), result?.downloadMbps, mbps, Icons.Rounded.ArrowDownward, Modifier.weight(1f))
            Tile(stringResource(Res.string.tools_speed_result_upload), result?.uploadMbps, mbps, Icons.Rounded.ArrowUpward, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(stringResource(Res.string.tools_speed_result_ping), result?.pingMs, ms, null, Modifier.weight(1f), latencyColor(result?.pingMs), decimals = 1)
            Tile(stringResource(Res.string.tools_speed_result_jitter), result?.jitterMs, ms, null, Modifier.weight(1f), decimals = 1)
        }
    }
}

@Composable
private fun Tile(
    label: String,
    value: Double?,
    unit: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    decimals: Int? = null,
) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (icon != null) Icon(icon, null, tint = SkipiTheme.colors.accent, modifier = Modifier.size(14.dp))
                Text(label, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value?.let { if (decimals != null) "%.${decimals}f".format(it) else formatSpeed(it.toFloat()) } ?: "--",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                    color = valueColor ?: colors.onSurface,
                    maxLines = 1,
                )
                Text(unit, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
            }
        }
    }
}
