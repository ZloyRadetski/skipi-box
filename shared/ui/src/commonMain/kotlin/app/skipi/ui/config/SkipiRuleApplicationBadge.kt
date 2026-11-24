// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.routing_rule_badge_partly_skipped
import app.skipi.ui.resources.routing_rule_badge_skipped
import app.skipi.ui.resources.routing_rule_badge_unknown_target
import app.skipi.ui.resources.routing_rule_reason_skipped
import app.skipi.ui.resources.routing_rule_reason_unknown_target
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import engine.xray.RuleApplication
import engine.xray.RuleApplicationStatus
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun RuleApplication.badgeColor(): Color {
    val isDark = SkipiTheme.colors.isDark
    return when (this) {
        RuleApplication.Skipped -> StatusColorDefaults.statusStopped(isDark)
        else -> StatusColorDefaults.pingMedium(isDark)
    }
}

/** Short chip saying that the running configuration does not apply this rule (or part of it); nothing when it does. */
@Composable
fun SkipiRuleApplicationBadge(status: RuleApplicationStatus, modifier: Modifier = Modifier) {
    if (status.isApplied) return
    val label = when (status.application) {
        RuleApplication.Skipped -> stringResource(Res.string.routing_rule_badge_skipped)
        RuleApplication.PartlySkipped -> stringResource(Res.string.routing_rule_badge_partly_skipped)
        else -> stringResource(Res.string.routing_rule_badge_unknown_target)
    }
    val color = status.application.badgeColor()
    Box(modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Supporting line under a rule: why the rule is not applied as written; nothing when it is. */
@Composable
fun SkipiRuleApplicationReason(status: RuleApplicationStatus, modifier: Modifier = Modifier) {
    if (status.isApplied) return
    val text = when (status.application) {
        RuleApplication.UnknownTarget -> stringResource(Res.string.routing_rule_reason_unknown_target)
        else -> stringResource(Res.string.routing_rule_reason_skipped).formatTemplate("values" to status.values.joinToString(", "))
    }
    Text(
        text = text,
        fontSize = 11.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
