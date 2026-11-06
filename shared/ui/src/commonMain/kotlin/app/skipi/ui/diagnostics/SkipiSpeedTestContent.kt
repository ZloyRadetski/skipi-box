// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class SpeedTestUiPhase { Idle, Ping, Download, Upload, Finished, Failed }

data class SpeedTestUiResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

private val DownloadPalette = SpeedGaugePalette(start = Color(0xFF12A8FF), end = Color(0xFF72FFD8))
private val UploadPalette = SpeedGaugePalette(start = Color(0xFFA95BFF), end = Color(0xFFFF7CC8))

/** The speed test as a dial: the live speed on a gauge while a transfer runs, the four results below it. */
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
) {
    val uploading = phase == SpeedTestUiPhase.Upload
    val palette = if (uploading) UploadPalette else DownloadPalette
    val running = phase == SpeedTestUiPhase.Ping || phase == SpeedTestUiPhase.Download || uploading
    // The dial shows the live speed while a transfer runs and the download result once the test is done.
    val shown = when (phase) {
        SpeedTestUiPhase.Download, SpeedTestUiPhase.Upload -> currentMbps ?: 0.0
        SpeedTestUiPhase.Finished -> result?.downloadMbps ?: 0.0
        else -> 0.0
    }
    val mbps = stringResource(Res.string.tools_speed_mbps)
    val ms = stringResource(Res.string.tools_speed_ms)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
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
                Spacer(Modifier.height(8.dp))
                SkipiSpeedGauge(
                    mbps = shown,
                    palette = palette,
                    trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    markColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                    activeMarkColor = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 420.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                        val headline = if (phase == SpeedTestUiPhase.Idle || phase == SpeedTestUiPhase.Ping) {
                            "--"
                        } else {
                            "%.2f".format(shown)
                        }
                        Text(headline, fontSize = 40.sp, fontWeight = FontWeight.Light, color = MiuixTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier.size(18.dp).border(1.5.dp, palette.start, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (uploading) "↑" else "↓", fontSize = 11.sp, color = palette.start)
                            }
                            Text(
                                stringResource(Res.string.tools_speed_mbps),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                if (phase == SpeedTestUiPhase.Failed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.tools_speed_failed, errorMessage.orEmpty()),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(if (running) progress.coerceIn(0f, 1f) else 0f).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(palette.start),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.5.dp, palette.start, RoundedCornerShape(24.dp))
                        .clickable { if (running) onStop() else onStart() }
                        .padding(horizontal = 36.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(if (running) Res.string.tools_speed_stop else Res.string.tools_speed_start),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.start,
                    )
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    SpeedMetric(stringResource(Res.string.tools_speed_result_ping), result?.pingMs, ms, Modifier.weight(1f))
                    SpeedMetric(stringResource(Res.string.tools_speed_result_jitter), result?.jitterMs, ms, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    SpeedMetric(stringResource(Res.string.tools_speed_result_download), result?.downloadMbps, mbps, Modifier.weight(1f), DownloadPalette.start)
                    SpeedMetric(stringResource(Res.string.tools_speed_result_upload), result?.uploadMbps, mbps, Modifier.weight(1f), UploadPalette.start)
                }
            }
        }
    }
}

@Composable
private fun SpeedMetric(label: String, value: Double?, unit: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = accent ?: MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value?.let { "%.1f".format(it) } ?: "--", fontSize = 22.sp, fontWeight = FontWeight.Light, color = MiuixTheme.colorScheme.onSurface)
            Text(unit, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
