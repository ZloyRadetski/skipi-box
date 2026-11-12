// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Http
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The "icon color" the user picked in the appearance settings; null means the theme's primary color. A host
 * provides it once above its settings pages, so every row on every page shows the same tile color.
 */
val LocalSettingsIconColor = compositionLocalOf<Color?> { null }

/** Provides the user's icon color (an ARGB long as it is stored) to every settings row below. */
@Composable
fun ProvideSettingsIconColor(argb: Long?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettingsIconColor provides argb?.let(::Color), content = content)
}

/** Edge of the small icon tile in front of a settings row. */
private val SettingIconTileSize = 32.dp
private val SettingIconGlyphSize = 18.dp

/** The small colored tile with an icon that opens a settings row. */
@Composable
fun SkipiSettingIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val tileColor = LocalSettingsIconColor.current ?: MiuixTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(SettingIconTileSize)
            .clip(RoundedCornerShape(10.dp))
            .background(tileColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SkipiTheme.colors.onAccent,
            modifier = Modifier.size(SettingIconGlyphSize),
        )
    }
}

/** Row icons a host needs but cannot import itself: only the shared UI module carries the icon set. */
object SkipiSettingRowIcons {
    val Http: ImageVector get() = Icons.Filled.Http
}

/**
 * The `startAction` of a Miuix preference row: `SwitchPreference(..., startAction = settingIcon(Icons.Filled.Lock))`.
 * Rows without an icon simply leave `startAction` out.
 */
fun settingIcon(icon: ImageVector): @Composable () -> Unit = { SkipiSettingIcon(icon) }
