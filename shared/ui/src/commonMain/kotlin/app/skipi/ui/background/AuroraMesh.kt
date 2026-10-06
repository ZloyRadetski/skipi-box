// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
fun DrawScope.drawAuroraMesh(
    isDark: Boolean,
    t1: Float,
    t2: Float,
    t3: Float,
    t4: Float,
) {
    val width = size.width
    val height = size.height
    val radius = maxOf(width, height) * 0.65f

    val blobColors = if (isDark) {
        listOf(
            Color(0xFF4F46E5),
            Color(0xFF7C3AED),
            Color(0xFF06B6D4),
            Color(0xFF10B981),
        )
    } else {
        listOf(
            Color(0xFF818CF8),
            Color(0xFFA78BFA),
            Color(0xFF22D3EE),
            Color(0xFF34D399),
        )
    }
    val blobAlphas = if (isDark) {
        listOf(0.40f, 0.34f, 0.30f, 0.22f)
    } else {
        listOf(0.50f, 0.42f, 0.38f, 0.28f)
    }

    val centers = listOf(
        Offset(width * (0.15f + 0.20f * t1), height * (0.10f + 0.16f * (1f - t2))),
        Offset(width * (0.85f - 0.22f * t2), height * (0.05f + 0.14f * t3)),
        Offset(width * (0.25f + 0.25f * (1f - t3)), height * (0.85f - 0.18f * t4)),
        Offset(width * (0.80f - 0.18f * t4), height * (0.90f - 0.22f * (1f - t1))),
    )

    centers.forEachIndexed { index, center ->
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    blobColors[index].copy(alpha = blobAlphas[index]),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            size = size,
        )
    }
}


