// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.tools_dns_exit_label
import app.skipi.ui.resources.tools_dns_failed
import app.skipi.ui.resources.tools_dns_failed_detail
import app.skipi.ui.resources.tools_dns_idle
import app.skipi.ui.resources.tools_dns_isp_unknown
import app.skipi.ui.resources.tools_dns_observed_egress
import app.skipi.ui.resources.tools_dns_observed_subnet
import app.skipi.ui.resources.tools_dns_reason_no_internet
import app.skipi.ui.resources.tools_dns_reason_tunnel_not_passing
import app.skipi.ui.resources.tools_dns_rerun
import app.skipi.ui.resources.tools_dns_stage_locating
import app.skipi.ui.resources.tools_dns_stage_probing
import app.skipi.ui.resources.tools_dns_start
import app.skipi.ui.resources.tools_dns_status_leak
import app.skipi.ui.resources.tools_dns_status_ok
import app.skipi.ui.resources.tools_dns_status_unknown
import app.skipi.ui.resources.tools_dns_stop
import app.skipi.ui.resources.tools_dns_summary_leak
import app.skipi.ui.resources.tools_dns_summary_no_leak
import app.skipi.ui.resources.tools_dns_summary_unknown
import app.skipi.ui.resources.tools_dns_title_failed
import app.skipi.ui.resources.tools_dns_title_idle
import app.skipi.ui.resources.tools_dns_title_leak
import app.skipi.ui.resources.tools_dns_title_no_leak
import app.skipi.ui.resources.tools_dns_title_running
import app.skipi.ui.resources.tools_dns_title_unknown
import app.skipi.ui.settings.readableOn
import app.skipi.ui.theme.SkipiTheme
import features.tools.dnsleak.DnsLeakExitInfo
import features.tools.dnsleak.DnsLeakResolverStatus
import features.tools.dnsleak.DnsLeakStage
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The verdict of the test at a glance: a status ring, what it means, the exit it was judged against, and the run button. */
@Composable
fun SkipiDnsLeakHero(
    model: DnsLeakScreenModel,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = model.headline.accent()
    val running = model.headline == DnsLeakHeadline.Running
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DnsStatusRing(headline = model.headline, accent = accent, running = running)
            Spacer(Modifier.height(14.dp))
            Text(
                text = model.headline.title(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (model.headline == DnsLeakHeadline.Idle || running) MiuixTheme.colorScheme.onSurface else accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = model.summary(),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            model.exit?.let { exit ->
                Spacer(Modifier.height(14.dp))
                DnsLeakExitTile(exit, Modifier.widthIn(max = 420.dp).fillMaxWidth())
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { if (running) onStop() else onStart() },
                colors = if (running) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            ) {
                val label = when {
                    running -> Res.string.tools_dns_stop
                    model.headline == DnsLeakHeadline.Idle -> Res.string.tools_dns_start
                    else -> Res.string.tools_dns_rerun
                }
                Text(
                    text = stringResource(label),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (running) MiuixTheme.colorScheme.onSurface else readableOn(MiuixTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** A ring with the state's icon in it; while the test runs an arc goes round it. */
@Composable
private fun DnsStatusRing(headline: DnsLeakHeadline, accent: Color, running: Boolean) {
    val track = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val turn by rememberInfiniteTransition(label = "dnsRing").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dnsRing",
    )
    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            when {
                running -> drawArc(accent, turn - 90f, 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                headline != DnsLeakHeadline.Idle -> drawArc(accent, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            }
        }
        Icon(
            imageVector = headline.icon(),
            contentDescription = null,
            tint = if (headline == DnsLeakHeadline.Idle) MiuixTheme.colorScheme.onSurfaceVariantSummary else accent,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun DnsLeakExitTile(exit: DnsLeakExitInfo, modifier: Modifier = Modifier) {
    val place = exit.countryName.ifBlank { exit.countryCode }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.tools_dns_exit_label),
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            listOf(exit.ip, place, exit.isp).filter(String::isNotBlank).joinToString(" · "),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** How many of the answering paths are fine, leak, or could not be judged; shown once there is a result. */
@Composable
fun SkipiDnsLeakStats(model: DnsLeakScreenModel, modifier: Modifier = Modifier) {
    if (model.systemRows.isEmpty() && model.publicRows.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DnsStatTile(stringResource(Res.string.tools_dns_status_ok), model.safeCount, SkipiTheme.colors.success, Modifier.weight(1f))
        DnsStatTile(stringResource(Res.string.tools_dns_status_leak), model.leakCount, SkipiTheme.colors.error, Modifier.weight(1f))
        DnsStatTile(stringResource(Res.string.tools_dns_status_unknown), model.unknownCount, MiuixTheme.colorScheme.onSurfaceVariantSummary, Modifier.weight(1f))
    }
}

@Composable
private fun DnsStatTile(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
            Text(
                text = count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                color = if (count == 0) MiuixTheme.colorScheme.onSurface else color,
            )
        }
    }
}

/** Resolvers of one group, one row each, in a single card. */
@Composable
fun SkipiDnsLeakRows(rows: List<DnsLeakRowModel>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                DnsLeakRow(row)
            }
        }
    }
}

@Composable
private fun DnsLeakRow(row: DnsLeakRowModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CountryBadge(row.countryCode)
        Column(Modifier.weight(1f)) {
            Text(
                row.provider.ifBlank { stringResource(Res.string.tools_dns_isp_unknown) },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val observed = row.observedIp?.let { ip ->
                stringResource(
                    if (row.viaSubnet) Res.string.tools_dns_observed_subnet else Res.string.tools_dns_observed_egress,
                    ip,
                )
            }
            Text(
                listOfNotNull(row.server, observed, row.asn?.let { "AS$it" }).joinToString(" · "),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusChip(row.status)
    }
}

/** Two-letter country code in a badge: regional-indicator flags render as plain letters on Windows, so none are used. */
@Composable
private fun CountryBadge(countryCode: String) {
    val code = countryCode.trim().uppercase().takeIf { it.length == 2 } ?: "··"
    Box(
        modifier = Modifier.size(width = 40.dp, height = 28.dp).clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusChip(status: DnsLeakResolverStatus) {
    val (label, color) = when (status) {
        DnsLeakResolverStatus.Safe -> stringResource(Res.string.tools_dns_status_ok) to SkipiTheme.colors.success
        DnsLeakResolverStatus.Leak -> stringResource(Res.string.tools_dns_status_leak) to SkipiTheme.colors.error
        DnsLeakResolverStatus.Unknown ->
            stringResource(Res.string.tools_dns_status_unknown) to MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Servers that gave no answer, named so that a path missing from the results is explained. */
@Composable
fun SkipiDnsLeakUnanswered(servers: List<String>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            servers.joinToString(", "),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun DnsLeakHeadline.accent(): Color = when (this) {
    DnsLeakHeadline.NoLeak -> SkipiTheme.colors.success
    DnsLeakHeadline.SuspectedLeak,
    DnsLeakHeadline.Failed,
    DnsLeakHeadline.FailedNoInternet,
    DnsLeakHeadline.FailedTunnel,
    -> SkipiTheme.colors.error

    DnsLeakHeadline.Unknown -> SkipiTheme.colors.warning
    DnsLeakHeadline.Idle,
    DnsLeakHeadline.Running,
    -> MiuixTheme.colorScheme.primary
}

private fun DnsLeakHeadline.icon(): ImageVector = when (this) {
    DnsLeakHeadline.NoLeak -> Icons.Rounded.Check
    DnsLeakHeadline.SuspectedLeak -> Icons.Rounded.PriorityHigh
    DnsLeakHeadline.Unknown -> Icons.Rounded.QuestionMark
    DnsLeakHeadline.Failed,
    DnsLeakHeadline.FailedNoInternet,
    DnsLeakHeadline.FailedTunnel,
    -> Icons.Rounded.Close

    DnsLeakHeadline.Idle,
    DnsLeakHeadline.Running,
    -> Icons.Rounded.Dns
}

@Composable
private fun DnsLeakHeadline.title(): String = stringResource(
    when (this) {
        DnsLeakHeadline.Idle -> Res.string.tools_dns_title_idle
        DnsLeakHeadline.Running -> Res.string.tools_dns_title_running
        DnsLeakHeadline.NoLeak -> Res.string.tools_dns_title_no_leak
        DnsLeakHeadline.SuspectedLeak -> Res.string.tools_dns_title_leak
        DnsLeakHeadline.Unknown -> Res.string.tools_dns_title_unknown
        DnsLeakHeadline.Failed,
        DnsLeakHeadline.FailedNoInternet,
        DnsLeakHeadline.FailedTunnel,
        -> Res.string.tools_dns_title_failed
    },
)

/** The sentence under the title: what the verdict rests on, the step in progress, or the cause of a failure. */
@Composable
private fun DnsLeakScreenModel.summary(): String = when (headline) {
    DnsLeakHeadline.Idle -> stringResource(Res.string.tools_dns_idle)
    DnsLeakHeadline.Running -> stringResource(
        if (stage == DnsLeakStage.Locating) Res.string.tools_dns_stage_locating else Res.string.tools_dns_stage_probing,
    )

    DnsLeakHeadline.NoLeak -> stringResource(Res.string.tools_dns_summary_no_leak)
    DnsLeakHeadline.SuspectedLeak -> stringResource(Res.string.tools_dns_summary_leak, leakCount)
    DnsLeakHeadline.Unknown -> stringResource(Res.string.tools_dns_summary_unknown)
    DnsLeakHeadline.FailedNoInternet -> stringResource(Res.string.tools_dns_reason_no_internet)
    DnsLeakHeadline.FailedTunnel -> stringResource(Res.string.tools_dns_reason_tunnel_not_passing)
    DnsLeakHeadline.Failed ->
        failureDetail?.let { stringResource(Res.string.tools_dns_failed_detail, it) } ?: stringResource(Res.string.tools_dns_failed)
}
