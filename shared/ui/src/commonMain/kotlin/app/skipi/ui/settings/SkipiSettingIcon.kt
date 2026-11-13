// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The "icon color" the user picked in the appearance settings; null means the theme's primary color. A host
 * provides it once above its settings pages, so every row on every page shows the same tile color.
 */
val LocalSettingsIconColor = compositionLocalOf<Color?> { null }

/** The color of the icon itself, picked in the appearance settings; null means black or white, whichever reads on the tile. */
val LocalSettingsIconGlyphColor = compositionLocalOf<Color?> { null }

/** Provides the user's icon background and icon colors (ARGB longs as they are stored) to every settings row below. */
@Composable
fun ProvideSettingsIconColor(argb: Long?, glyphArgb: Long? = null, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettingsIconColor provides argb?.let(::Color),
        LocalSettingsIconGlyphColor provides glyphArgb?.let(::Color),
        content = content,
    )
}

/** The color of an icon drawn on a tile of [background]: the one the user picked, else black or white by contrast. */
internal fun settingIconGlyphColor(background: Color, picked: Color?): Color = picked ?: readableOn(background)

/** Edge of the small tile in front of a settings row, and the shape every such tile shares. */
private val SettingIconTileSize = 38.dp
private val SettingIconGlyphSize = 22.dp
private val SettingIconTileShape = RoundedCornerShape(12.dp)

/**
 * The tile is drawn this far left of where Miuix lays out a start action, into the row's own inset, so it sits
 * closer to the card edge while the text stays where it was; that also widens the gap to the text to 14 dp.
 */
private val SettingIconShiftLeft = 6.dp

@Composable
private fun settingIconColor(): Color = LocalSettingsIconColor.current ?: MiuixTheme.colorScheme.primary

/**
 * The small tile with an icon in front of a settings row: the user's icon color with a glyph that reads on it, like the
 * tiles of the settings home. A soft tint was tried first, but a dark icon color vanished on a dark card.
 */
@Composable
fun SkipiSettingIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val color = settingIconColor()
    Box(
        modifier = modifier
            .offset(x = -SettingIconShiftLeft)
            .size(SettingIconTileSize)
            .clip(SettingIconTileShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = settingIconGlyphColor(color, LocalSettingsIconGlyphColor.current),
            modifier = Modifier.size(SettingIconGlyphSize),
        )
    }
}

/** The tile of a color row: the color itself, on the same grid as the icon tiles so the text lines up. */
@Composable
fun SkipiSettingColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = -SettingIconShiftLeft)
            .size(SettingIconTileSize)
            .clip(SettingIconTileShape)
            .background(color)
            .border(1.dp, MiuixTheme.colorScheme.dividerLine, SettingIconTileShape),
    )
}

/**
 * The `startAction` of a Miuix preference row: `SwitchPreference(..., startAction = settingIcon(Icons.Rounded.Lock))`.
 * Rows without an icon simply leave `startAction` out.
 */
fun settingIcon(icon: ImageVector): @Composable () -> Unit = { SkipiSettingIcon(icon) }
