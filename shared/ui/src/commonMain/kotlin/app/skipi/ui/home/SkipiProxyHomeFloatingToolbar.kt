// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import app.skipi.ui.components.onSecondaryClick
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

/** Which buttons the compact toolbar shows. The ping button is always available, whether or not the tunnel is up. */
internal data class FloatingToolbarButtons(val toggle: Boolean, val ping: Boolean) {
    val anyVisible: Boolean get() = toggle || ping
}

internal fun floatingToolbarButtons(showToggleAction: Boolean, showPingAction: Boolean) =
    FloatingToolbarButtons(toggle = showToggleAction, ping = showPingAction)

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
    /** Long press (right click on desktop) on the ping button: ping everything on the page. Null hides the gesture. */
    onTestAllLatency: (() -> Unit)? = null,
) {
    val buttons = floatingToolbarButtons(showToggleAction, showPingAction)
    if (!buttons.anyVisible) return

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
            if (buttons.ping) {
                PingToolbarButton(
                    testing = isTestingLatency,
                    enabled = canTestLatency,
                    description = latencyActionDescription,
                    onClick = onTestLatency,
                    onLongClick = onTestAllLatency?.takeIf { !isTestingLatency },
                )
            }
            if (buttons.toggle) {
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

/** Ping button with a second gesture: long press on touch, right click with a mouse. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PingToolbarButton(
    testing: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val onAccent = SkipiTheme.colors.onAccent
    val longClick: (() -> Unit)? = onLongClick?.let { action ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .onSecondaryClick(enabled = longClick != null) { longClick?.invoke() }
            // The page ping can be available while pinging the one selected server is not, so the long press
            // keeps the button alive on its own.
            .combinedClickable(
                enabled = enabled || longClick != null,
                role = Role.Button,
                onClick = { if (enabled) onClick() },
                onLongClick = longClick,
            )
            .graphicsLayer { alpha = if (enabled || longClick != null) 1f else 0.38f },
        contentAlignment = Alignment.Center,
    ) {
        if (testing) {
            SkipiProxyHeroAnimatedHourglassIcon(color = onAccent, size = 24.dp)
        } else {
            SkipiProxyHeroStaticHourglassIcon(
                modifier = Modifier.size(26.dp).semantics { contentDescription = description },
                color = onAccent,
                size = 20.dp,
            )
        }
    }
}
