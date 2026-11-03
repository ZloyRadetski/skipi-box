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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import app.skipi.ui.resources.home_runtime_outbound_metric
import app.skipi.ui.resources.proxy_traffic_stats_notification_disconnect
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The connection card Home used before the redesign, kept as the "Legacy" connection widget: a framed card with
 * the power button on the left, the title and server beside it and the live facts below.
 */
@Composable
fun SkipiProxyHeroLegacyCard(
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
    val metrics = SkipiTheme.homeMetrics
    val heroShape = SkipiTheme.shapes.large
    val cardPadding = if (compact) SkipiTheme.spacing.medium else SkipiTheme.spacing.large
    val outerButtonSize = metrics.heroButtonOuter
    val buttonSize = metrics.heroButton
    val spinnerSize = metrics.heroButtonOuter
    val buttonIconSize = metrics.heroIcon
    val cardBorder by animateColorAsState(
        targetValue = when {
            connected -> colors.accent.copy(alpha = 0.35f)
            connecting -> colors.accent.copy(alpha = 0.45f)
            else -> colors.border
        },
        animationSpec = tween(350),
        label = "skipiProxyHeroLegacyBorder",
    )
    val buttonBackground by animateColorAsState(
        targetValue = when {
            connected -> colors.accent
            connecting -> colors.accent.copy(alpha = 0.22f)
            else -> colors.raisedSurface
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroLegacyButtonBackground",
    )
    val buttonBorder by animateColorAsState(
        targetValue = when {
            connected -> colors.accent.copy(alpha = 0.45f)
            connecting -> colors.accent
            else -> colors.border.copy(alpha = 1.5f).legacyCoerceAlpha()
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroLegacyButtonBorder",
    )
    val accentIsBright = remember(colors.accent) { colors.accent.luminance() > 0.65f }
    val powerTint by animateColorAsState(
        targetValue = when {
            connected -> if (accentIsBright) Color(0xFF1B1B1F) else Color.White
            connecting -> colors.accent
            else -> colors.mutedText.copy(alpha = 0.5f)
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroLegacyPowerTint",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyHeroLegacyPressScale",
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
        label = "skipiProxyHeroLegacyStateScale",
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
                    Box(
                        modifier = Modifier
                            .size(buttonSize)
                            .graphicsLayer {
                                // Read while drawing: the breathing pulse must not recompose the card every frame.
                                val scale = stateScale * breathScale * pressScale
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
                        label = "skipiProxyHeroLegacyTitle",
                    ) { title ->
                        Text(
                            text = title,
                            fontSize = if (compact) SkipiTheme.typography.titleLarge.fontSize else SkipiTheme.typography.headlineSmall.fontSize,
                            fontWeight = typography.title,
                            color = colors.text,
                        )
                    }
                    Spacer(Modifier.height(SkipiTheme.spacing.extraSmall))
                    AnimatedContent(
                        targetState = state.subtitle,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)))
                                .togetherWith(fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(160)))
                        },
                        label = "skipiProxyHeroLegacySubtitle",
                    ) { subtitle ->
                        Text(
                            text = subtitle,
                            fontSize = SkipiTheme.typography.bodyMedium.fontSize,
                            fontWeight = typography.body,
                            color = colors.mutedText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = SkipiTheme.typography.bodyMedium.lineHeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.profileText?.let { profileText ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = profileText,
                            fontSize = SkipiTheme.typography.bodySmall.fontSize,
                            fontWeight = typography.body,
                            color = colors.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (compact) {
                val ipTile = skipiHeroIpTile(state.phase, state.ip, state.showIp, state.maskIp)
                AnimatedVisibility(
                    visible = connected && (state.sessionDuration != null || ipTile != null),
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        // One raised panel for the live facts, like the tiles of the wide layout.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.raisedSurface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                        if (state.sessionDuration != null) Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.accent),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = state.sessionDuration?.value.orEmpty(),
                                fontSize = 13.sp,
                                fontWeight = typography.body,
                                color = colors.mutedText,
                            )
                            state.latency?.let { latency ->
                                SkipiProxyHeroLegacyMetricSeparator(colors.mutedText)
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
                                SkipiProxyHeroLegacyMetricSeparator(colors.mutedText)
                                Text(
                                    text = stringResource(Res.string.home_runtime_outbound_metric, memoryText),
                                    fontSize = 13.sp,
                                    fontWeight = typography.body,
                                    color = colors.mutedText,
                                )
                            }
                        }
                        ipTile?.let { tile ->
                            if (state.sessionDuration != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.mutedText.copy(alpha = 0.18f)),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (tile.flag != null) {
                                    SkipiProxyServerFlagBadge(
                                        flag = tile.flag,
                                        containerColor = Color.Transparent,
                                        size = 18.dp,
                                        shapeRadius = 4.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = tile.value,
                                    fontSize = 14.sp,
                                    fontWeight = typography.emphasis,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        }
                    }
                }
            } else {
                val tiles = skipiHeroInfoTiles(
                    phase = state.phase,
                    sessionDurationText = state.sessionDuration?.let { "" },
                    latency = state.latency,
                    memoryText = state.memoryText,
                    ip = state.ip,
                    showIp = state.showIp,
                    maskIp = state.maskIp,
                )
                AnimatedVisibility(
                    visible = tiles.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        SkipiHeroInfoTileGrid(
                            tiles = tiles,
                            surface = colors.raisedSurface.copy(alpha = 0.6f),
                            labelColor = colors.mutedText,
                            valueColor = colors.text,
                            fontWeight = typography.emphasis,
                            timeValue = state.sessionDuration,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipiProxyHeroLegacyMetricSeparator(color: Color) {
    Spacer(Modifier.width(8.dp))
    Text(text = "|", fontSize = 12.sp, color = color.copy(alpha = 0.35f))
    Spacer(Modifier.width(8.dp))
}

private fun Color.legacyCoerceAlpha(): Color = copy(alpha = alpha.coerceIn(0f, 1f))
