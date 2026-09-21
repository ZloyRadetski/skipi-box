// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SkipiConnectionHeroPhase {
    Disconnected,
    Connecting,
    Connected,
}

data class SkipiConnectionHeroState(
    val phase: SkipiConnectionHeroPhase,
    val title: String,
    val subtitle: String,
    val profileText: String? = null,
    val toggleEnabled: Boolean,
)

data class SkipiConnectionHeroColors(
    val surface: Color,
    val raisedSurface: Color,
    val border: Color,
    val text: Color,
    val mutedText: Color,
    val connected: Color,
    val connecting: Color,
    val disconnected: Color,
)

/**
 * Shared primary connection card. Hosts supply localized presentation text and
 * map their tunnel runtime to [SkipiConnectionHeroState]; the visual state,
 * sizing and power control are common on Android and desktop.
 */
@Composable
fun SkipiConnectionHeroCard(
    state: SkipiConnectionHeroState,
    colors: SkipiConnectionHeroColors,
    compact: Boolean,
    connectContentDescription: String,
    disconnectContentDescription: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (state.phase) {
        SkipiConnectionHeroPhase.Connected -> colors.connected
        SkipiConnectionHeroPhase.Connecting -> colors.connecting
        SkipiConnectionHeroPhase.Disconnected -> colors.disconnected
    }
    val iconTint = when (state.phase) {
        SkipiConnectionHeroPhase.Connected -> colors.connected
        SkipiConnectionHeroPhase.Connecting -> colors.connecting
        SkipiConnectionHeroPhase.Disconnected -> colors.mutedText
    }
    val horizontalPadding = if (compact) 20.dp else 26.dp
    val verticalPadding = if (compact) 16.dp else 24.dp
    val buttonOuterSize = if (compact) 78.dp else 96.dp
    val buttonSize = if (compact) 70.dp else 86.dp
    val iconSize = if (compact) 40.dp else 48.dp

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            1.dp,
            when (state.phase) {
                SkipiConnectionHeroPhase.Connected -> colors.connected.copy(alpha = 0.48f)
                SkipiConnectionHeroPhase.Connecting -> colors.connecting.copy(alpha = 0.48f)
                SkipiConnectionHeroPhase.Disconnected -> colors.border
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(buttonOuterSize), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .background(colors.raisedSurface)
                        .border(3.dp, statusColor, CircleShape)
                        .clickable(enabled = state.toggleEnabled, onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = if (state.phase == SkipiConnectionHeroPhase.Connected) {
                            disconnectContentDescription
                        } else {
                            connectContentDescription
                        },
                        tint = iconTint,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 14.dp else 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = state.title,
                    color = colors.text,
                    fontSize = if (compact) 25.sp else 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = state.subtitle,
                    color = colors.mutedText,
                    fontSize = if (compact) 16.sp else 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.profileText?.let { profileText ->
                    Text(
                        text = profileText,
                        color = colors.mutedText,
                        fontSize = if (compact) 12.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
