// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Settings
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
