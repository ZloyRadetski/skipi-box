// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.skipi.ui.home.MenuMaxWidth
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Room around the surface that holds its shadow, so the popup window does not cut it off. */
private val ShadowMargin: Dp = 8.dp
private val MenuMinWidth: Dp = 112.dp
private val MenuMaxHeight: Dp = 480.dp
private val RowMinHeight: Dp = 48.dp

/**
 * Where the popup window goes so its surface (the popup minus [shadowPad] on every side) opens under the
 * [anchor], aligned to the side of the window the anchor is in: flipped to the other side
 * when the surface would leave the window, above the anchor
 * when there is no room below, and inside the window when neither fits.
 */
internal fun skipiMenuPosition(
    anchor: IntRect,
    window: IntSize,
    popup: IntSize,
    rtl: Boolean,
    shadowPad: Int,
): IntOffset {
    val surfaceWidth = popup.width - 2 * shadowPad
    val surfaceHeight = popup.height - 2 * shadowPad
    val leftAligned = anchor.left
    val rightAligned = anchor.right - surfaceWidth
    // A card wider than the menu opens it at the card's trailing end, where its action buttons are. A button
    // opens it on the side of the window it sits in, so the menu grows toward the middle of the window.
    val preferRight = if (anchor.width > surfaceWidth) !rtl else anchor.center.x * 2 > window.width
    val preferred = if (preferRight) rightAligned else leftAligned
    val alternative = if (preferRight) leftAligned else rightAligned
    val x = when {
        preferred >= 0 && preferred + surfaceWidth <= window.width -> preferred
        alternative >= 0 && alternative + surfaceWidth <= window.width -> alternative
        else -> (window.width - surfaceWidth).coerceAtLeast(0).coerceAtMost(preferred.coerceAtLeast(0))
    }
    val below = anchor.bottom
    val above = anchor.top - surfaceHeight
    val y = when {
        below + surfaceHeight <= window.height -> below
        above >= 0 -> above
        else -> (window.height - surfaceHeight).coerceAtLeast(0)
    }
    return IntOffset(x - shadowPad, y - shadowPad)
}

/**
 * The app's own menu surface: a popup that opens next to the layout it is composed in, with the app's palette,
 * a soft shadow and its own open and close motion. It is not a Material3 menu. A tap outside it, Back or Escape
 * asks to dismiss it. [modifier] sizes the column that holds the rows, which is where the menu animates its width.
 */
@Composable
internal fun SkipiMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Color = SkipiTheme.colors.surface,
    text: Color = SkipiTheme.colors.onSurface,
    outline: Color = SkipiTheme.colors.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shadowPad = with(density) { ShadowMargin.roundToPx() }
    val position = remember(rtl, shadowPad) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = skipiMenuPosition(anchorBounds, windowSize, popupContentSize, rtl, shadowPad)
        }
    }
    val shape = SkipiTheme.shapes.large

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(120)) + scaleIn(
                animationSpec = tween(160),
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f),
            ),
            exit = fadeOut(tween(100)) + scaleOut(
                animationSpec = tween(100),
                targetScale = 0.95f,
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f),
            ),
        ) {
            Box(Modifier.padding(ShadowMargin)) {
                Column(
                    modifier = modifier
                        .shadow(8.dp, shape)
                        .clip(shape)
                        .background(surface)
                        .border(BorderStroke(1.dp, outline.copy(alpha = 0.2f)), shape)
                        .padding(vertical = 8.dp)
                        .width(IntrinsicSize.Max)
                        .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                        .heightIn(max = MenuMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // A popup is not under a Material surface, so the text style the rows read would be the library
                    // default, not the font the appearance settings chose. The app's text style is passed in here.
                    ProvideTextStyle(menuTextStyle()) {
                        CompositionLocalProvider(LocalContentColor provides text) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One tappable row of a [SkipiMenuPopup]: an optional leading icon, the label and an optional trailing mark.
 * A disabled row is dimmed and ignores taps.
 */
@Composable
internal fun SkipiMenuRowItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: Color = SkipiTheme.colors.onSurface,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    val contentColor = if (enabled) text else text.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (leadingIcon != null) {
                Box(Modifier.padding(end = 12.dp)) { leadingIcon() }
            }
            Box(Modifier.weight(1f, fill = false)) { label() }
            if (trailingIcon != null) {
                Box(Modifier.padding(start = 12.dp)) { trailingIcon() }
            }
        }
    }
}

/**
 * The app's own text style without its color: a row takes its color from the menu (muted when disabled, accent when
 * selected), which the style's own color would override.
 */
@Composable
private fun menuTextStyle(): TextStyle = MiuixTheme.textStyles.main.copy(color = Color.Unspecified)

/** The size of a menu row's text, smaller than the Material default in every menu of the app. */
internal val MenuRowTextStyle: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)

/** [MenuRowTextStyle] on the app's own text style, so a row has the font, weight and scale the settings chose. */
@Composable
internal fun menuRowTextStyle(): TextStyle = menuTextStyle().merge(MenuRowTextStyle)

/** A single-line or wrapped menu row label in the menu's row text style. */
@Composable
internal fun SkipiMenuRowLabel(
    text: String,
    maxLines: Int = 2,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = menuRowTextStyle(),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
