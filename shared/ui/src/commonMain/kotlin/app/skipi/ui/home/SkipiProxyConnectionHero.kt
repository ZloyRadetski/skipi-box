// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.connection_status_tap_to_connect
import app.skipi.ui.resources.proxy_traffic_stats_notification_disconnect
import org.jetbrains.compose.resources.stringResource

data class SkipiProxyHeroLatency(
    val text: String,
    val color: Color,
    val testing: Boolean = false,
)

/** Host-neutral state for the detailed Android/desktop Proxy Home hero. */
data class SkipiProxyHeroState(
    val phase: SkipiConnectionHeroPhase,
    val title: String,
    val subtitle: String,
    val profileText: String? = null,
    val flag: String? = null,
    val sessionDurationText: String? = null,
    val latency: SkipiProxyHeroLatency? = null,
    val memoryText: String? = null,
    val toggleEnabled: Boolean = true,
)

data class SkipiProxyHeroColors(
    val surface: Color,
    val raisedSurface: Color,
    val border: Color,
    val accent: Color,
    val text: Color,
    val mutedText: Color,
    val connectedStatus: Color,
)

data class SkipiProxyHeroTypography(
    val title: FontWeight = FontWeight.Bold,
    val emphasis: FontWeight = FontWeight.SemiBold,
    val body: FontWeight = FontWeight.Medium,
)

/**
 * Detailed primary connection card. Tunnel sampling and localized strings are
 * supplied by a host; the visual state machine and animations are shared.
 */
@Composable
fun SkipiProxyHeroClassicCard(
    state: SkipiProxyHeroState,
    colors: SkipiProxyHeroColors,
    typography: SkipiProxyHeroTypography,
    compact: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    connectContentDescription: String = stringResource(Res.string.connection_status_tap_to_connect),
    disconnectContentDescription: String = stringResource(Res.string.proxy_traffic_stats_notification_disconnect),
) {
    val connected = state.phase == SkipiConnectionHeroPhase.Connected
    val connecting = state.phase == SkipiConnectionHeroPhase.Connecting
    val heroShape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    val cardPadding = if (compact) 14.dp else 18.dp
    val outerButtonSize = if (compact) 82.dp else 96.dp
    val buttonSize = if (compact) 72.dp else 86.dp
    val spinnerSize = if (compact) 78.dp else 92.dp
    val buttonIconSize = if (compact) 36.dp else 42.dp
    val cardBorder by animateColorAsState(
        targetValue = when {
            connected -> colors.accent.copy(alpha = 0.35f)
            connecting -> colors.accent.copy(alpha = 0.45f)
            else -> colors.border
        },
        animationSpec = tween(350),
        label = "skipiProxyHeroClassicBorder",
    )
    val buttonBackground by animateColorAsState(
        targetValue = when {
            connected -> colors.accent
            connecting -> colors.accent.copy(alpha = 0.22f)
            else -> colors.raisedSurface
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroClassicButtonBackground",
    )
    val buttonBorder by animateColorAsState(
        targetValue = when {
            connected -> colors.accent.copy(alpha = 0.45f)
            connecting -> colors.accent
            else -> colors.border.copy(alpha = 1.5f).coerceAlpha()
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroClassicButtonBorder",
    )
    val accentIsBright = remember(colors.accent) { colors.accent.luminance() > 0.65f }
    val powerTint by animateColorAsState(
        targetValue = when {
            connected -> if (accentIsBright) Color(0xFF1B1B1F) else Color.White
            connecting -> colors.accent
            else -> colors.mutedText.copy(alpha = 0.5f)
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroClassicPowerTint",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyHeroClassicPressScale",
    )
    val stateScale by animateFloatAsState(
        targetValue = when {
            connected -> 1f
            connecting -> 0.98f
            else -> 0.96f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyHeroClassicStateScale",
    )
    val breathScale by rememberSkipiProxyHeroPulse(
        enabled = connected,
        initialValue = 1f,
        targetValue = 1.035f,
        durationMillis = 1600,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(heroShape)
            .border(1.dp, cardBorder, heroShape),
        colors = CardDefaults.defaultColors(color = colors.surface),
        insideMargin = PaddingValues(horizontal = cardPadding, vertical = cardPadding),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(outerButtonSize), contentAlignment = Alignment.Center) {
                    if (connected) SkipiProxyHeroPowerButtonWaves(accent = colors.accent, size = buttonSize)
                    if (connecting) SkipiProxyHeroConnectingSpinner(accent = colors.accent, size = spinnerSize)
                    val scale = stateScale * breathScale * pressScale
                    Box(
                        modifier = Modifier
                            .size(buttonSize)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(buttonBackground)
                            .border(
                                width = if (connected) 3.dp else 2.dp,
                                color = buttonBorder,
                                shape = CircleShape,
                            )
                            .semantics {
                                contentDescription = if (connected) {
                                    disconnectContentDescription
                                } else {
                                    connectContentDescription
                                }
                                role = Role.Button
                            }
                            .clickable(
                                enabled = state.toggleEnabled,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onToggle,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        SkipiProxyHeroPowerIcon(
                            color = powerTint,
                            modifier = Modifier.size(buttonIconSize),
                        )
                    }
                }
                Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = state.title,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(260)) +
                                slideInVertically(animationSpec = tween(260)) { height -> height / 3 })
                                .togetherWith(
                                    fadeOut(animationSpec = tween(160)) +
                                        slideOutVertically(animationSpec = tween(160)) { height -> -height / 3 },
                                )
                        },
                        label = "skipiProxyHeroClassicTitle",
                    ) { title ->
                        Text(
                            text = title,
                            fontSize = if (compact) 20.sp else 22.sp,
                            fontWeight = typography.title,
                            color = colors.text,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = state.subtitle,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)))
                                .togetherWith(fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)))
                        },
                        label = "skipiProxyHeroClassicSubtitle",
                    ) { subtitle ->
                        Text(
                            text = subtitle,
                            fontSize = if (compact) 14.sp else 15.sp,
                            fontWeight = typography.body,
                            color = colors.mutedText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.profileText?.let { profileText ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = profileText,
                            fontSize = 12.sp,
                            fontWeight = typography.body,
                            color = colors.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = connected && state.sessionDurationText != null,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
            ) {
                val sessionDuration = state.sessionDurationText.orEmpty()
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.accent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = sessionDuration,
                            fontSize = 13.sp,
                            fontWeight = typography.body,
                            color = colors.mutedText,
                        )
                        state.latency?.let { latency ->
                            SkipiProxyHeroMetricSeparator(colors.mutedText)
                            if (latency.testing) {
                                SkipiProxyHeroAnimatedHourglassIcon(
                                    color = latency.color,
                                    size = 13.dp,
                                )
                            } else {
                                SkipiProxyHeroSignalBarsIcon(
                                    color = latency.color,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = latency.text,
                                fontSize = 13.sp,
                                fontWeight = typography.body,
                                color = latency.color,
                            )
                        }
                        state.memoryText?.let { memoryText ->
                            SkipiProxyHeroMetricSeparator(colors.mutedText)
                            Text(
                                text = memoryText,
                                fontSize = 13.sp,
                                fontWeight = typography.body,
                                color = colors.mutedText,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact connection hero, suitable where tunnel power is controlled separately. */
@Composable
fun SkipiProxyHeroCompactCard(
    state: SkipiProxyHeroState,
    colors: SkipiProxyHeroColors,
    typography: SkipiProxyHeroTypography,
    fallbackBadgePainter: Painter?,
    modifier: Modifier = Modifier,
) {
    val connected = state.phase == SkipiConnectionHeroPhase.Connected
    val connecting = state.phase == SkipiConnectionHeroPhase.Connecting
    val heroShape = RoundedCornerShape(18.dp)
    val background by animateColorAsState(
        targetValue = if (connected) colors.accent else colors.surface,
        animationSpec = tween(350),
        label = "skipiProxyHeroCompactBackground",
    )
    val border by animateColorAsState(
        targetValue = when {
            connected -> colors.accent.copy(alpha = 0.35f)
            connecting -> colors.accent.copy(alpha = 0.45f)
            else -> colors.border
        },
        animationSpec = tween(350),
        label = "skipiProxyHeroCompactBorder",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (connected || connecting) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyHeroCompactDotScale",
    )
    val connectingPulse by rememberSkipiProxyHeroPulse(
        enabled = connecting,
        initialValue = 0.7f,
        targetValue = 1.35f,
        durationMillis = 800,
    )

    Card(
        modifier = modifier.clip(heroShape).border(1.dp, border, heroShape),
        colors = CardDefaults.defaultColors(color = background),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState = connected,
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + expandVertically(animationSpec = tween(280)))
                    .togetherWith(fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180)))
            },
            label = "skipiProxyHeroCompactContent",
        ) { isConnected ->
            if (isConnected) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        scaleX = dotScale
                                        scaleY = dotScale
                                    }
                                    .clip(CircleShape)
                                    .background(colors.connectedStatus),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = state.title,
                                fontSize = 12.sp,
                                fontWeight = typography.body,
                                color = colors.connectedStatus,
                            )
                        }
                        state.memoryText?.let { memoryText ->
                            Text(
                                text = memoryText,
                                fontSize = 12.sp,
                                fontWeight = typography.body,
                                color = colors.mutedText,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkipiProxyHeroBadge(state.flag, fallbackBadgePainter)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = state.subtitle,
                            fontSize = 16.sp,
                            fontWeight = typography.emphasis,
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        state.latency?.let { latency ->
                            Spacer(Modifier.width(8.dp))
                            SkipiProxyHeroLatencyChip(latency, typography.body)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkipiProxyHeroBadge(state.flag, fallbackBadgePainter)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        val scale = if (connecting) connectingPulse else dotScale
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(CircleShape)
                                    .background(if (connecting) colors.accent else colors.mutedText.copy(alpha = 0.5f)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = state.title,
                                fontSize = 15.sp,
                                fontWeight = typography.emphasis,
                                color = if (connecting) colors.accent else colors.text,
                            )
                        }
                        Text(
                            text = state.subtitle,
                            fontSize = 12.sp,
                            color = colors.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    state.latency?.let { latency ->
                        Spacer(Modifier.width(8.dp))
                        SkipiProxyHeroLatencyChip(latency, typography.body)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipiProxyHeroBadge(
    flag: String?,
    fallbackPainter: Painter?,
) {
    if (fallbackPainter != null) {
        SkipiProxyServerFlagBadge(
            flag = flag,
            fallbackPainter = fallbackPainter,
            containerColor = Color.White.copy(alpha = 0.07f),
            fallbackTint = Color.White.copy(alpha = 0.65f),
            size = 36.dp,
            shapeRadius = 8.dp,
        )
    } else {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(flag ?: "⚡", fontSize = 20.sp)
        }
    }
}

@Composable
private fun SkipiProxyHeroLatencyChip(
    latency: SkipiProxyHeroLatency,
    fontWeight: FontWeight,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(latency.color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        if (latency.testing) {
            SkipiProxyHeroAnimatedHourglassIcon(color = latency.color, size = 12.dp)
        } else {
            SkipiProxyHeroSignalBarsIcon(color = latency.color, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = latency.text,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            color = latency.color,
        )
    }
}

@Composable
private fun SkipiProxyHeroMetricSeparator(color: Color) {
    Spacer(Modifier.width(8.dp))
    Text(text = "|", fontSize = 12.sp, color = color.copy(alpha = 0.35f))
    Spacer(Modifier.width(8.dp))
}

private fun Color.coerceAlpha(): Color = copy(alpha = alpha.coerceIn(0f, 1f))
