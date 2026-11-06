// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The marks of the dial. They are spread evenly, so the low speeds that matter most get the room. */
internal val SpeedGaugeMarks = listOf(0, 5, 10, 50, 100, 250, 500, 750, 1000)

/** Where [mbps] sits on the dial, 0f at the first mark and 1f at the last; beyond the scale it stays at 1f. */
internal fun speedGaugeFraction(mbps: Double): Float {
    if (mbps <= 0.0) return 0f
    val last = SpeedGaugeMarks.lastIndex
    if (mbps >= SpeedGaugeMarks[last]) return 1f
    val upper = SpeedGaugeMarks.indexOfFirst { mark -> mark > mbps }
    val lower = upper - 1
    val within = (mbps - SpeedGaugeMarks[lower]) / (SpeedGaugeMarks[upper] - SpeedGaugeMarks[lower])
    return ((lower + within) / last).toFloat()
}

/** The two ends of the arc a phase draws in, from where the needle starts to where it points. */
internal data class SpeedGaugePalette(val start: Color, val end: Color)

/**
 * A speedometer dial in the manner of speed-test apps: a 270 degree arc that fills up to the current speed,
 * the marks along its inside and a needle. It draws only; the number and the unit belong to the caller.
 */
@Composable
internal fun SkipiSpeedGauge(
    mbps: Double,
    palette: SpeedGaugePalette,
    trackColor: Color,
    markColor: Color,
    activeMarkColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val fraction by animateFloatAsState(
        targetValue = speedGaugeFraction(mbps),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
    )
    val measurer = rememberTextMeasurer()
    Box(modifier = modifier.fillMaxWidth().aspectRatio(1.18f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.18f)) {
            val diameter = min(size.width, size.height * 1.18f) * 0.94f
            val stroke = diameter * 0.115f
            val radius = (diameter - stroke) / 2f
            val center = Offset(size.width / 2f, diameter / 2f + stroke * 0.25f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // The dial opens at the bottom: it runs from 135 degrees clockwise over the top to 45 degrees.
            rotate(degrees = StartAngle, pivot = center) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = Sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (fraction > 0.001f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to palette.start,
                                // The colors span the filled part only, so the tip is always the end color.
                                (Sweep * fraction / 360f) to palette.end,
                                1f to palette.end,
                            ),
                            center = center,
                        ),
                        startAngle = 0f,
                        sweepAngle = Sweep * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
            }

            // The marks sit just inside the arc, white up to the needle and dim after it.
            val labelRadius = radius - stroke * 0.5f - diameter * 0.075f
            SpeedGaugeMarks.forEachIndexed { index, mark ->
                val at = index.toFloat() / SpeedGaugeMarks.lastIndex
                val angle = Math.toRadians((StartAngle + Sweep * at).toDouble())
                val layout = measurer.measure(
                    text = mark.toString(),
                    style = TextStyle(
                        fontSize = (diameter * 0.04f).toSp(),
                        fontWeight = FontWeight.Bold,
                        color = if (at <= fraction + 0.001f) activeMarkColor else markColor,
                    ),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        center.x + labelRadius * cos(angle).toFloat() - layout.size.width / 2f,
                        center.y + labelRadius * sin(angle).toFloat() - layout.size.height / 2f,
                    ),
                )
            }

            drawNeedle(center = center, angleDegrees = StartAngle + Sweep * fraction, outer = labelRadius * 0.86f, inner = radius * 0.34f, width = diameter * 0.026f)
        }
        content()
    }
}

/** A tapered wedge that fades towards the middle of the dial, like the needle of a speed-test dial. */
private fun DrawScope.drawNeedle(center: Offset, angleDegrees: Float, outer: Float, inner: Float, width: Float) {
    rotate(degrees = angleDegrees, pivot = center) {
        val path = Path().apply {
            moveTo(center.x + inner, center.y - width * 0.15f)
            lineTo(center.x + outer, center.y - width / 2f)
            lineTo(center.x + outer, center.y + width / 2f)
            lineTo(center.x + inner, center.y + width * 0.15f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.95f)),
                start = Offset(center.x + inner, center.y),
                end = Offset(center.x + outer, center.y),
            ),
        )
    }
}

private const val StartAngle = 135f
private const val Sweep = 270f
