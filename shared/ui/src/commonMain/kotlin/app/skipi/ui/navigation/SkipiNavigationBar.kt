// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Tune
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.nav_configs
import app.skipi.ui.resources.nav_proxy
import app.skipi.ui.resources.nav_settings
import org.jetbrains.compose.resources.stringResource

/** A host-localized item rendered by the common SKIPI navigation chrome. */
class SkipiNavigationItem(
    val destination: SkipiMainDestination,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun defaultSkipiNavigationItems(): List<SkipiNavigationItem> = listOf(
    SkipiNavigationItem(
        destination = SkipiMainDestination.Proxy,
        label = stringResource(Res.string.nav_proxy),
        icon = Icons.AutoMirrored.Outlined.List,
    ),
    SkipiNavigationItem(
        destination = SkipiMainDestination.Configs,
        label = stringResource(Res.string.nav_configs),
        icon = Icons.Outlined.Tune,
    ),
    SkipiNavigationItem(
        destination = SkipiMainDestination.Settings,
        label = stringResource(Res.string.nav_settings),
        icon = Icons.Outlined.Hexagon,
    ),
)

/**
 * Shared bottom navigation for compact and desktop hosts.
 *
 * Platform code supplies localized labels/icons and actions only. The layout,
 * selection state and interaction semantics live in common Compose code.
 */
@Composable
fun SkipiNavigationBar(
    selectedDestination: SkipiMainDestination,
    onSelect: (SkipiMainDestination) -> Unit,
    modifier: Modifier = Modifier,
    items: List<SkipiNavigationItem> = defaultSkipiNavigationItems(),
    contentColor: Color = Color(0xFFF4F4F6),
    inactiveContentColor: Color = Color(0xFF9A9DA8),
    backgroundColor: Color = Color(0xFF121316),
    containerColor: Color = Color(0xFF202126),
    selectedContainerColor: Color = Color(0xFF747474),
    borderColor: Color = Color(0xFF35373E),
    horizontalPadding: Dp = 28.dp,
    verticalPadding: Dp = 14.dp,
    maxWidth: Dp = 980.dp,
    height: Dp = 82.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .height(height),
            color = containerColor,
            shape = RoundedCornerShape(38.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            shadowElevation = 10.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items.forEach { item ->
                    val selected = item.destination == selectedDestination
                    val itemContentColor = if (selected) contentColor else inactiveContentColor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (selected) selectedContainerColor else Color.Transparent)
                            .clickable { onSelect(item.destination) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = itemContentColor,
                        )
                        Text(
                            text = item.label,
                            color = itemContentColor,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
