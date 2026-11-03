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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.Placeholder
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.app.home.ProxyHomeButtonSize
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.connection_status_tap_to_connect
import app.skipi.ui.resources.home_runtime_outbound_metric
import app.skipi.ui.resources.proxy_traffic_stats_notification_disconnect
import app.skipi.ui.theme.SkipiTheme
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
    /** Ticking session time; read only by the leaf text so a tick never recomposes the hero. */
    val sessionDuration: State<String>? = null,
    val latency: SkipiProxyHeroLatency? = null,
    val memoryText: String? = null,
    /** Tunnel public address; null until resolved. Shown only when [showIp] is on. */
    val ip: SkipiHeroIp? = null,
    val showIp: Boolean = false,
    val maskIp: Boolean = false,
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
    val onAccent: Color = Color.White,
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkipiProxyHeroClassicCard(
    state: SkipiProxyHeroState,
    colors: SkipiProxyHeroColors,
    typography: SkipiProxyHeroTypography,
    compact: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: ProxyHomeButtonSize = ProxyHomeButtonSize.Large,
    connectContentDescription: String = stringResource(Res.string.connection_status_tap_to_connect),
    disconnectContentDescription: String = stringResource(Res.string.proxy_traffic_stats_notification_disconnect),
) {
    val connected = state.phase == SkipiConnectionHeroPhase.Connected
    val connecting = state.phase == SkipiConnectionHeroPhase.Connecting
    val outerSize = skipiClassicButtonOuterSize(buttonSize, compact)
    // The ring is the outer edge of the whole control, so there is no empty margin above or below it.
    val buttonSize = outerSize / SkipiClassicButtonRingRatio
    // A thin ring with a clear gap around the button; it takes the accent while the tunnel is up.
    val ringSize = outerSize
    val ringColor by animateColorAsState(
        targetValue = if (connected) colors.accent.copy(alpha = 0.5f) else colors.text.copy(alpha = 0.16f),
        animationSpec = tween(300),
        label = "skipiProxyHeroRing",
    )
    val accentIsBright = remember(colors.accent) { colors.accent.luminance() > 0.65f }
    val buttonBackground by animateColorAsState(
        targetValue = if (connected) colors.accent else colors.raisedSurface,
        animationSpec = tween(300),
        label = "skipiProxyHeroButtonBackground",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            connected -> if (accentIsBright) Color(0xFF1B1B1F) else Color.White
            connecting -> colors.accent
            state.toggleEnabled -> colors.text
            else -> colors.mutedText.copy(alpha = 0.5f)
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroIconTint",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyHeroPressScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = if (compact) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(outerSize), contentAlignment = Alignment.Center) {
            if (connected) SkipiProxyHeroPowerButtonWaves(accent = colors.accent, size = buttonSize)
            if (connecting) {
                SkipiProxyHeroConnectingSpinner(accent = colors.accent, size = ringSize)
            } else {
                Box(Modifier.size(ringSize).border(1.5.dp, ringColor, CircleShape))
            }
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(CircleShape)
                    .background(buttonBackground)
                    .semantics {
                        contentDescription = if (connected) disconnectContentDescription else connectContentDescription
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
                SkipiProxyHeroPowerIcon(color = iconTint, modifier = Modifier.size(buttonSize * 0.42f))
            }
        }
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        // The badge is part of the first line of the name, so a long name wraps under it naturally.
        val nameSize = if (state.subtitle.length > 22) 18.sp else if (compact) 20.sp else 24.sp
        val badgeSize = nameSize * 1.25f
        val badgeContainer = if (state.flag == null || state.flag == StrategyFlagMarker) {
            colors.text.copy(alpha = 0.07f)
        } else {
            Color.Transparent
        }
        val nameText = remember(state.subtitle) {
            buildAnnotatedString {
                appendInlineContent("badge", "□")
                append("  ")
                append(state.subtitle)
            }
        }
        Text(
            text = nameText,
            inlineContent = mapOf(
                "badge" to InlineTextContent(
                    Placeholder(badgeSize, badgeSize, PlaceholderVerticalAlign.Center),
                ) {
                    SkipiProxyServerFlagBadge(
                        flag = state.flag,
                        containerColor = badgeContainer,
                        size = with(LocalDensity.current) { badgeSize.toDp() },
                        shapeRadius = 6.dp,
                    )
                },
            ),
            fontSize = nameSize,
            lineHeight = nameSize * 1.3f,
            fontWeight = typography.title,
            color = colors.text,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        state.profileText?.let { profileText ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = profileText,
                fontSize = 13.sp,
                fontWeight = typography.body,
                color = colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

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
            // One quiet line of live facts instead of a grid of tiles.
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                tiles.forEach { tile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tile.flag != null) {
                            SkipiProxyServerFlagBadge(
                                flag = tile.flag,
                                containerColor = Color.Transparent,
                                size = 16.dp,
                                shapeRadius = 3.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = if (tile.kind == SkipiHeroTileKind.Time) {
                                state.sessionDuration?.value.orEmpty()
                            } else {
                                tile.value
                            },
                            fontSize = 13.sp,
                            fontWeight = typography.body,
                            color = tile.color ?: colors.mutedText,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Compact connection hero, suitable where tunnel power is controlled separately or inline. */
@Composable
fun SkipiProxyHeroCompactCard(
    state: SkipiProxyHeroState,
    colors: SkipiProxyHeroColors,
    typography: SkipiProxyHeroTypography,
    fallbackBadgePainter: Painter?,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
    connectContentDescription: String = stringResource(Res.string.connection_status_tap_to_connect),
    disconnectContentDescription: String = stringResource(Res.string.proxy_traffic_stats_notification_disconnect),
) {
    val connected = state.phase == SkipiConnectionHeroPhase.Connected
    val connecting = state.phase == SkipiConnectionHeroPhase.Connecting
    val heroShape = RoundedCornerShape(18.dp)
    val background by animateColorAsState(
        targetValue = skipiProxyHeroCompactBackground(colors.surface, colors.accent, connected),
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
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        state.memoryText?.let { memoryText ->
                            Text(
                                text = stringResource(Res.string.home_runtime_outbound_metric, memoryText),
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
                        if (onToggle != null) {
                            Spacer(Modifier.width(10.dp))
                            SkipiProxyHeroCompactPowerButton(
                                connected = true,
                                connecting = connecting,
                                enabled = state.toggleEnabled,
                                colors = colors,
                                onToggle = onToggle,
                                connectContentDescription = connectContentDescription,
                                disconnectContentDescription = disconnectContentDescription,
                            )
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
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                    if (onToggle != null) {
                        Spacer(Modifier.width(10.dp))
                        SkipiProxyHeroCompactPowerButton(
                            connected = false,
                            connecting = connecting,
                            enabled = state.toggleEnabled,
                            colors = colors,
                            onToggle = onToggle,
                            connectContentDescription = connectContentDescription,
                            disconnectContentDescription = disconnectContentDescription,
                        )
                    }
                }
            }
        }
    }
}

internal fun skipiProxyHeroCompactBackground(
    surface: Color,
    accent: Color,
    connected: Boolean,
): Color = if (connected) lerp(surface, accent, 0.08f) else surface

@Composable
private fun SkipiProxyHeroCompactPowerButton(
    connected: Boolean,
    connecting: Boolean,
    enabled: Boolean,
    colors: SkipiProxyHeroColors,
    onToggle: () -> Unit,
    connectContentDescription: String,
    disconnectContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyHeroCompactPowerPressScale",
    )
    val buttonBackground by animateColorAsState(
        targetValue = when {
            connected -> colors.accent
            connecting -> colors.accent.copy(alpha = 0.15f)
            else -> colors.raisedSurface
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroCompactPowerBg",
    )
    val buttonBorder by animateColorAsState(
        targetValue = when {
            connected -> colors.accent.copy(alpha = 0.45f)
            connecting -> colors.accent
            else -> colors.border
        },
        animationSpec = tween(300),
        label = "skipiProxyHeroCompactPowerBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            connected -> colors.onAccent
            connecting -> colors.accent
            enabled -> colors.accent
            else -> colors.mutedText.copy(alpha = 0.5f)
        },
        animationSpec = tween(250),
        label = "skipiProxyHeroCompactPowerIconTint",
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(buttonBackground)
            .border(1.5.dp, buttonBorder, CircleShape)
            .semantics {
                contentDescription = if (connected) disconnectContentDescription else connectContentDescription
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (connecting) {
            SkipiProxyHeroConnectingSpinner(
                accent = if (connected) colors.onAccent else colors.accent,
                size = 20.dp,
            )
        } else {
            SkipiProxyHeroPowerIcon(
                color = iconTint,
                modifier = Modifier.size(16.dp),
            )
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
            if (flag == StrategyFlagMarker) {
                SkipiProxyBalancerIcon(color = SkipiTheme.colors.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            } else if (flag == null) {
                SkipiProxyServerIcon(color = SkipiTheme.colors.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(22.dp))
            } else {
                Text(flag, fontSize = 20.sp)
            }
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
