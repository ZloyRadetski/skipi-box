// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import app.skipi.ui.theme.SkipiTheme
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** Shared country/emoji badge. Hosts supply their own fallback asset and colors. */
@Composable
fun SkipiProxyServerFlagBadge(
    flag: String?,
    fallbackPainter: Painter? = null,
    containerColor: Color = Color.White.copy(alpha = 0.07f),
    fallbackTint: Color = Color.White.copy(alpha = 0.65f),
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shapeRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(shapeRadius)
    val flagBitmap = rememberCountryFlagBitmap(flag)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (flag == StrategyFlagMarker) {
            SkipiProxyBalancerIcon(color = SkipiTheme.colors.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.58f))
        } else if (flagBitmap != null) {
            SkipiCountryFlagImage(flagBitmap, Modifier.fillMaxSize())
        } else if (flag != null) {
            Text(
                text = flag,
                fontSize = (size.value * 0.58f).sp,
                lineHeight = (size.value * 0.58f).sp,
            )
        } else if (fallbackPainter != null) {
            Icon(
                painter = fallbackPainter,
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.size(size * 0.62f),
            )
        } else {
            SkipiProxyServerIcon(color = SkipiTheme.colors.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(size * 0.56f))
        }
    }
}

/** The flag value the presentation layer uses to mark a strategy (balancer) group, which has no country. */
internal const val StrategyFlagMarker = "⚡"

/** Drawn balancer icon: two routes crossing, traffic shuffled between them. Replaces the lightning emoji. */
@Composable
fun SkipiProxyBalancerIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        fun route(startY: Float, endY: Float) {
            val path = Path().apply {
                moveTo(w * 0.08f, h * startY)
                cubicTo(w * 0.5f, h * startY, w * 0.5f, h * endY, w * 0.78f, h * endY)
            }
            drawPath(path, color, style = stroke)
            val head = Path().apply {
                moveTo(w * 0.94f, h * endY)
                lineTo(w * 0.76f, h * (endY - 0.14f))
                lineTo(w * 0.76f, h * (endY + 0.14f))
                close()
            }
            drawPath(head, color)
        }
        route(startY = 0.3f, endY = 0.7f)
        route(startY = 0.7f, endY = 0.3f)
    }
}

/** Drawn server icon for a manually added server that has no country flag. */
@Composable
fun SkipiProxyServerIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f)
        val radius = CornerRadius(w * 0.12f, w * 0.12f)
        listOf(0.1f to 0.45f, 0.55f to 0.9f).forEach { (top, bottom) ->
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.08f, h * top),
                size = Size(w * 0.84f, h * (bottom - top)),
                cornerRadius = radius,
                style = stroke,
            )
            drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.25f, h * (top + bottom) / 2f))
            drawLine(
                color = color,
                start = Offset(w * 0.45f, h * (top + bottom) / 2f),
                end = Offset(w * 0.75f, h * (top + bottom) / 2f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Shared compact protocol tag; a host resolves its protocol color. */
@Composable
fun SkipiProxyProtocolChip(
    text: String,
    chipColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: Boolean = false,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(chipColor.copy(alpha = if (selected) 0.22f else 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = fontWeight,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shared compact transport tag; a host resolves the selected-theme colors. */
@Composable
fun SkipiProxyTransportChip(
    text: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
