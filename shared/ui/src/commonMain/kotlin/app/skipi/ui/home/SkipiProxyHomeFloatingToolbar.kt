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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_connection_connect
import app.skipi.ui.resources.home_connection_disconnect
import app.skipi.ui.resources.proxy_server_list_latency_test
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun SkipiProxyHomeFloatingToolbar(
    running: Boolean,
    isConnecting: Boolean,
    isTestingLatency: Boolean,
    canToggleTunnel: Boolean,
    canTestLatency: Boolean,
    showToggleAction: Boolean,
    showPingAction: Boolean,
    onToggleRunning: () -> Unit,
    onTestLatency: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shouldShow = showToggleAction || (showPingAction && running)
    if (!shouldShow) return

    val latencyActionDescription = stringResource(Res.string.proxy_server_list_latency_test)
    val accent = SkipiTheme.colors.accent
    val onAccent = SkipiTheme.colors.onAccent
    val animatedFabColor by animateColorAsState(
        targetValue = if (running) accent else accent.copy(alpha = 0.95f),
        animationSpec = tween(300),
        label = "skipi_fab_color_anim",
    )
    val toggleInteractionSource = remember { MutableInteractionSource() }
    val togglePressed by toggleInteractionSource.collectIsPressedAsState()
    val toggleScale by animateFloatAsState(
        targetValue = if (togglePressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipi_fab_toggle_scale",
    )

    Surface(
        modifier = modifier.padding(end = 20.dp, bottom = 20.dp),
        shape = RoundedCornerShape(32.dp),
        color = animatedFabColor,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPingAction) {
                if (showToggleAction) {
                    AnimatedVisibility(
                        visible = running,
                        enter = slideInHorizontally(initialOffsetX = { it }) + expandHorizontally(expandFrom = Alignment.End),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + shrinkHorizontally(shrinkTowards = Alignment.End),
                    ) {
                        IconButton(
                            onClick = onTestLatency,
                            enabled = canTestLatency,
                            modifier = Modifier.size(52.dp),
                        ) {
                            if (isTestingLatency) {
                                SkipiProxyHeroAnimatedHourglassIcon(
                                    color = onAccent,
                                    size = 24.dp,
                                )
                            } else {
                                SkipiProxyHeroStaticHourglassIcon(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .semantics { contentDescription = latencyActionDescription },
                                    color = onAccent,
                                    size = 20.dp,
                                )
                            }
                        }
                    }
                } else if (running) {
                    IconButton(
                        onClick = onTestLatency,
                        enabled = canTestLatency,
                        modifier = Modifier.size(52.dp),
                    ) {
                        if (isTestingLatency) {
                            SkipiProxyHeroAnimatedHourglassIcon(
                                color = onAccent,
                                size = 24.dp,
                            )
                        } else {
                            SkipiProxyHeroStaticHourglassIcon(
                                modifier = Modifier
                                    .size(26.dp)
                                    .semantics { contentDescription = latencyActionDescription },
                                color = onAccent,
                                size = 20.dp,
                            )
                        }
                    }
                }
            }
            if (showToggleAction) {
                IconButton(
                    onClick = onToggleRunning,
                    enabled = canToggleTunnel,
                    interactionSource = toggleInteractionSource,
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            scaleX = toggleScale
                            scaleY = toggleScale
                        },
                ) {
                    AnimatedContent(
                        targetState = when {
                            isConnecting -> 1
                            running -> 2
                            else -> 0
                        },
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 40)) + scaleIn(initialScale = 0.65f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                                .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.65f))
                        },
                        label = "skipi_fab_play_pause_anim",
                    ) { buttonState ->
                        when (buttonState) {
                            1 -> SkipiProxyHeroConnectingSpinner(accent = onAccent, size = 32.dp)
                            2 -> Icon(
                                imageVector = Icons.Outlined.Pause,
                                contentDescription = stringResource(Res.string.home_connection_disconnect),
                                tint = onAccent,
                                modifier = Modifier.size(28.dp),
                            )
                            else -> Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = stringResource(Res.string.home_connection_connect),
                                tint = onAccent,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
