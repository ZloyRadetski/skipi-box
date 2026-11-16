// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import app.skipi.ui.settings.LocalIconColor
import app.skipi.ui.theme.SkipiTheme

/**
 * The color an icon is drawn in. A color given on purpose ([explicit], for a status or a brand) is kept; every
 * other icon takes the color the user picked in the appearance settings; without one it takes [fallback] (a state
 * color the screen computes), else [onAccent] on an accent fill, else the theme's own icon color ([themeIcon]).
 * Unspecified leaves the host's default.
 */
internal fun resolveIconTint(
    explicit: Color,
    picked: Color?,
    themeIcon: Color,
    onAccent: Color?,
    fallback: Color = Color.Unspecified,
): Color =
    explicit.takeIf { it.isSpecified } ?: picked ?: fallback.takeIf { it.isSpecified } ?: onAccent ?: themeIcon

@Composable
private fun appIconTint(explicit: Color, onAccent: Boolean, fallback: Color): Color {
    val colors = SkipiTheme.colors
    return resolveIconTint(
        explicit = explicit,
        picked = LocalIconColor.current,
        themeIcon = colors.icon,
        onAccent = colors.onAccent.takeIf { onAccent },
        fallback = fallback,
    )
}

/**
 * The icon of the app. Every icon goes through here, so the "Icon color" setting colors all of them; give [tint]
 * only for a color that carries meaning (a status, a brand), [fallbackTint] for a state color that the user's pick
 * replaces, and [onAccent] for an icon drawn on an accent fill.
 */
@Composable
fun SkipiIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onAccent: Boolean = false,
    fallbackTint: Color = Color.Unspecified,
) {
    val resolved = appIconTint(tint, onAccent, fallbackTint)
    if (resolved.isSpecified) {
        top.yukonga.miuix.kmp.basic.Icon(imageVector, contentDescription, modifier, resolved)
    } else {
        top.yukonga.miuix.kmp.basic.Icon(imageVector, contentDescription, modifier)
    }
}

@Composable
fun SkipiIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onAccent: Boolean = false,
    fallbackTint: Color = Color.Unspecified,
) {
    val resolved = appIconTint(tint, onAccent, fallbackTint)
    if (resolved.isSpecified) {
        top.yukonga.miuix.kmp.basic.Icon(painter, contentDescription, modifier, resolved)
    } else {
        top.yukonga.miuix.kmp.basic.Icon(painter, contentDescription, modifier)
    }
}

/** The same icon for a place that follows its container's content color (Material surfaces) when nothing is picked. */
@Composable
fun SkipiContentIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onAccent: Boolean = false,
    fallbackTint: Color = Color.Unspecified,
) {
    val resolved = appIconTint(tint, onAccent, fallbackTint)
    if (resolved.isSpecified) {
        androidx.compose.material3.Icon(imageVector, contentDescription, modifier, resolved)
    } else {
        androidx.compose.material3.Icon(imageVector, contentDescription, modifier)
    }
}

@Composable
fun SkipiContentIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onAccent: Boolean = false,
    fallbackTint: Color = Color.Unspecified,
) {
    val resolved = appIconTint(tint, onAccent, fallbackTint)
    if (resolved.isSpecified) {
        androidx.compose.material3.Icon(painter, contentDescription, modifier, resolved)
    } else {
        androidx.compose.material3.Icon(painter, contentDescription, modifier)
    }
}

/** For an icon the app draws itself: the picked or theme icon color with the [default]'s own transparency. */
internal fun resolveDrawnIconColor(picked: Color?, themeIcon: Color, default: Color): Color =
    (picked ?: themeIcon.takeIf { it.isSpecified })?.copy(alpha = default.alpha) ?: default

/** The color for an icon the app draws itself (not a vector [SkipiIcon]); [default] is its look without a pick. */
@Composable
fun skipiDrawnIconColor(default: Color): Color =
    resolveDrawnIconColor(LocalIconColor.current, SkipiTheme.colors.icon, default)
