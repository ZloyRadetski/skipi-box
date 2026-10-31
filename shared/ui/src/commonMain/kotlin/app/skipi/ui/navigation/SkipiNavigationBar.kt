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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Settings
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.nav_configs
import app.skipi.ui.resources.nav_proxy
import app.skipi.ui.resources.nav_settings
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

/** A host-localized item rendered by the common SKIPI navigation chrome. */
class SkipiNavigationItem(
    val destination: SkipiMainDestination,
    val label: String,
    val icon: ImageVector,
)

/** The bar's icon for a destination; Android's set is the reference for every host. */
val SkipiMainDestination.navigationIcon: ImageVector
    get() = when (this) {
        SkipiMainDestination.Proxy -> MiuixIcons.Layers
        SkipiMainDestination.Configs -> MiuixIcons.MindMap
        SkipiMainDestination.Settings -> MiuixIcons.Settings
    }

@Composable
fun defaultSkipiNavigationItems(): List<SkipiNavigationItem> = listOf(
    SkipiNavigationItem(
        destination = SkipiMainDestination.Proxy,
        label = stringResource(Res.string.nav_proxy),
        icon = SkipiMainDestination.Proxy.navigationIcon,
    ),
    SkipiNavigationItem(
        destination = SkipiMainDestination.Configs,
        label = stringResource(Res.string.nav_configs),
        icon = SkipiMainDestination.Configs.navigationIcon,
    ),
    SkipiNavigationItem(
        destination = SkipiMainDestination.Settings,
        label = stringResource(Res.string.nav_settings),
        icon = SkipiMainDestination.Settings.navigationIcon,
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
    contentColor: Color? = null,
    inactiveContentColor: Color? = null,
    backgroundColor: Color? = null,
    containerColor: Color? = null,
    selectedContainerColor: Color? = null,
    borderColor: Color? = null,
    horizontalPadding: Dp = 28.dp,
    verticalPadding: Dp = 14.dp,
    maxWidth: Dp = 980.dp,
    height: Dp = 82.dp,
) {
    val colors = SkipiTheme.colors
    val resolvedContentColor = contentColor ?: colors.onSurface
    val resolvedInactiveContentColor = inactiveContentColor ?: colors.onSurfaceVariant
    val resolvedBackgroundColor = backgroundColor ?: colors.background
    val resolvedContainerColor = containerColor ?: colors.surface
    val resolvedSelectedContainerColor = selectedContainerColor
        ?: colors.accent.copy(alpha = if (colors.isDark) 0.30f else 0.16f)
    val resolvedBorderColor = borderColor ?: colors.onSurface.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(resolvedBackgroundColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .height(height),
            color = resolvedContainerColor,
            shape = SkipiTheme.shapes.extraLarge,
            border = androidx.compose.foundation.BorderStroke(1.dp, resolvedBorderColor),
            shadowElevation = SkipiTheme.elevation.large,
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(SkipiTheme.spacing.small)) {
                items.forEach { item ->
                    val selected = item.destination == selectedDestination
                    val itemContentColor = if (selected) resolvedContentColor else resolvedInactiveContentColor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(SkipiTheme.shapes.large)
                            .background(if (selected) resolvedSelectedContainerColor else Color.Transparent)
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
                            style = SkipiTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
