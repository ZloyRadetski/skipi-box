// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.tools_speed_failed
import app.skipi.ui.resources.tools_speed_mbps
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class SpeedTestUiPhase { Idle, Ping, Download, Upload, Finished, Failed }

data class SpeedTestUiResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
)

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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
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
                val headline = when (phase) {
                    SpeedTestUiPhase.Download, SpeedTestUiPhase.Upload -> "%.1f".format(currentMbps ?: 0.0)
                    else -> result?.let { "%.1f".format(it.downloadMbps) } ?: "--"
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(headline, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                    Text(
                        stringResource(Res.string.tools_speed_mbps),
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                    )
                }
                if (phase == SpeedTestUiPhase.Failed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.tools_speed_failed, errorMessage.orEmpty()),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = .15f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp)
                            .clip(RoundedCornerShape(3.dp)).background(MiuixTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(16.dp))
                val running = phase == SpeedTestUiPhase.Ping || phase == SpeedTestUiPhase.Download || phase == SpeedTestUiPhase.Upload
                TextButton(
                    text = stringResource(if (running) Res.string.tools_speed_stop else Res.string.tools_speed_start),
                    onClick = { if (running) onStop() else onStart() },
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                SpeedMetric(stringResource(Res.string.tools_speed_result_ping), result?.pingMs)
                SpeedMetric(stringResource(Res.string.tools_speed_result_jitter), result?.jitterMs)
                SpeedMetric(stringResource(Res.string.tools_speed_result_download), result?.downloadMbps)
                SpeedMetric(stringResource(Res.string.tools_speed_result_upload), result?.uploadMbps)
            }
        }
    }
}

@Composable
private fun SpeedMetric(label: String, value: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value?.let { "%.1f".format(it) } ?: "--", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
