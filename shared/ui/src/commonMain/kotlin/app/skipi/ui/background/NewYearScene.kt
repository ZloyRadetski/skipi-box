// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.TimeSource

/** One falling snowflake; positions are fractions of the canvas, sizes are in dp-like pixels at 1x. */
data class SnowflakeSpec(
    val x: Float,
    val phase: Float,
    val radius: Float,
    val fallMillis: Long,
    val swayAmplitude: Float,
)

/** A garland bulb hanging [y] below the top edge on the wire. */
data class GarlandBulb(val x: Float, val y: Float, val colorIndex: Int)

val NewYearBulbColors: List<Color> = listOf(
    Color(0xFFFF4D6D),
    Color(0xFFFFC53D),
    Color(0xFF3DDC97),
    Color(0xFF4CC9F0),
    Color(0xFFB388FF),
)

private const val GarlandSpanWidth = 250f
private const val GarlandFrameMillis = 40L

fun newYearSnowflakes(count: Int, seed: Int): List<SnowflakeSpec> {
    val random = Random(seed)
    return List(count) {
        SnowflakeSpec(
            x = random.nextFloat(),
            phase = random.nextFloat(),
            radius = 1.2f + random.nextFloat() * 2.4f,
            fallMillis = 9_000L + random.nextLong(9_000L),
            swayAmplitude = 0.01f + random.nextFloat() * 0.02f,
        )
    }
}

/** How far down (0 top .. 1 bottom) the flake is after [elapsedMillis]; it wraps back to the top. */
fun snowfallProgress(flake: SnowflakeSpec, elapsedMillis: Long): Float {
    val raw = flake.phase + elapsedMillis.coerceAtLeast(0L).toFloat() / flake.fallMillis
    return raw - kotlin.math.floor(raw)
}

/** Depth of the wire below its fixing points at [x] inside one span: a parabola, deepest in the middle. */
fun garlandY(x: Float, spanWidth: Float, sag: Float): Float {
    val t = (x / spanWidth).coerceIn(0f, 1f)
    return sag * 4f * t * (1f - t)
}

/** The bulbs of a garland across [width]: the wire hangs in spans of about 250 px, a bulb every [spacing]. */
fun garlandBulbs(
    width: Float,
    spacing: Float,
    sag: Float,
    spanTarget: Float = GarlandSpanWidth,
): List<GarlandBulb> {
    if (width <= 0f || spacing <= 0f) return emptyList()
    val spans = (width / spanTarget).roundToInt().coerceAtLeast(1)
    val spanWidth = width / spans
    val count = (width / spacing).toInt()
    return List(count) { index ->
        val x = (index + 0.5f) * spacing
        val local = x - spanWidth * (x / spanWidth).toInt().coerceAtMost(spans - 1)
        GarlandBulb(x = x, y = garlandY(local, spanWidth, sag), colorIndex = index % NewYearBulbColors.size)
    }
}

/** Brightness of a bulb, between 0.46 and 1; neighbouring bulbs are out of step. */
fun twinkleAlpha(bulbIndex: Int, elapsedMillis: Long): Float {
    val angle = elapsedMillis.toDouble() / 1_600.0 * 2.0 * PI + bulbIndex * 1.7
    return (0.73 + 0.27 * sin(angle)).toFloat()
}

/**
 * The New Year decoration: a garland under the top edge and slow snow. It draws above the page and takes
 * no touches; with [reduceMotion] it stands still.
 */
@Composable
fun NewYearOverlay(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val flakes = remember { newYearSnowflakes(count = 38, seed = 2027) }
    val elapsed = remember { mutableLongStateOf(0L) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            val started = TimeSource.Monotonic.markNow()
            while (true) {
                // Waiting for a frame means a stopped or covered window costs nothing.
                withFrameNanos { elapsed.longValue = started.elapsedNow().inWholeMilliseconds }
                delay(GarlandFrameMillis)
            }
        }
    }
    val cache = remember { NewYearDrawCache() }
    Canvas(modifier.fillMaxSize().graphicsLayer { }) {
        drawSnow(flakes, elapsed.longValue, cache)
        drawGarland(elapsed.longValue, cache)
    }
}

/**
 * What the draw passes would otherwise rebuild every frame: the size-dependent geometry of the garland and the glow
 * brushes, which are centred on the origin and moved into place, with the per-frame brightness applied as the draw alpha.
 * Used from the draw pass only.
 */
private class NewYearDrawCache {
    private var snowDensity = Float.NaN
    private val snowGlow = HashMap<Float, Brush>()

    fun snowGlow(density: Float, radius: Float): Brush {
        if (density != snowDensity) {
            snowGlow.clear()
            snowDensity = density
        }
        return snowGlow.getOrPut(radius) {
            Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0.35f), Color.Transparent),
                center = Offset.Zero,
                radius = radius,
            )
        }
    }

    class Garland(
        val unit: Float,
        val wire: Path,
        val bulbs: List<GarlandBulb>,
        val body: Path,
        val glows: List<Brush>,
    )

    private var garlandWidth = Float.NaN
    private var garlandUnit = Float.NaN
    private var garland: Garland? = null

    fun garland(width: Float, unit: Float): Garland {
        garland?.let { if (garlandWidth == width && garlandUnit == unit) return it }
        val sag = 12f * unit
        val spanTarget = GarlandSpanWidth * unit
        val spans = (width / spanTarget).roundToInt().coerceAtLeast(1)
        val spanWidth = width / spans
        val wire = Path().apply {
            moveTo(0f, 0f)
            for (span in 0 until spans) {
                val left = span * spanWidth
                quadraticTo(left + spanWidth / 2f, sag * 2f, left + spanWidth, 0f)
            }
        }
        val r = 3.6f * unit
        val length = r * 3.1f
        val body = Path().apply {
            moveTo(-r * 0.5f, 0f)
            cubicTo(-r * 1.35f, length * 0.28f, -r * 1.15f, length * 0.95f, 0f, length)
            cubicTo(r * 1.15f, length * 0.95f, r * 1.35f, length * 0.28f, r * 0.5f, 0f)
            close()
        }
        val glows = NewYearBulbColors.map { color ->
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.34f), color.copy(alpha = 0.10f), Color.Transparent),
                center = Offset.Zero,
                radius = r * 5.5f,
            )
        }
        return Garland(
            unit = unit,
            wire = wire,
            bulbs = garlandBulbs(width = width, spacing = 32f * unit, sag = sag, spanTarget = spanTarget),
            body = body,
            glows = glows,
        ).also {
            garland = it
            garlandWidth = width
            garlandUnit = unit
        }
    }
}

private val WireDark = Color(0xFF0A2417)
private val WireLight = Color(0xFF2F7D4F)

private fun DrawScope.drawSnow(flakes: List<SnowflakeSpec>, elapsedMillis: Long, cache: NewYearDrawCache) {
    val density = (size.minDimension / 400f).coerceIn(0.8f, 1.6f)
    for (flake in flakes) {
        val progress = snowfallProgress(flake, elapsedMillis)
        val sway = sin((progress * 2f * PI.toFloat()) + flake.phase * 6f) * flake.swayAmplitude
        // Depth: small flakes are far (faint), big ones near (brighter, with a star shape).
        val depth = ((flake.radius - 1.2f) / 2.4f).coerceIn(0f, 1f)
        // Fade in and out at the top and bottom so flakes never pop.
        val edge = (minOf(progress, 1f - progress) * 12f).coerceIn(0f, 1f)
        val alpha = (0.28f + 0.42f * depth) * edge
        val center = Offset((flake.x + sway) * size.width, progress * size.height)
        val radius = flake.radius * 0.7f * density
        translate(left = center.x, top = center.y) {
            drawCircle(
                brush = cache.snowGlow(density, radius * 2f),
                radius = radius * 2f,
                center = Offset.Zero,
                alpha = alpha,
            )
        }
        if (depth > 0.65f) {
            val arm = radius * 1.7f
            val spin = progress * 360f * 1.5f + flake.phase * 90f
            for (k in 0 until 3) {
                rotate(degrees = spin + k * 60f, pivot = center) {
                    drawLine(
                        Color.White.copy(alpha = alpha * 0.9f),
                        Offset(center.x, center.y - arm),
                        Offset(center.x, center.y + arm),
                        strokeWidth = 0.9f * density,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGarland(elapsedMillis: Long, cache: NewYearDrawCache) {
    val unit = (size.minDimension / 400f).coerceIn(0.8f, 1.5f)
    val geometry = cache.garland(size.width, unit)
    drawPath(geometry.wire, color = WireDark, style = Stroke(width = 2.4f * unit, cap = StrokeCap.Round))
    drawPath(geometry.wire, color = WireLight, style = Stroke(width = 1.1f * unit, cap = StrokeCap.Round))

    val bulbs = geometry.bulbs
    val body = geometry.body
    val r = 3.6f * unit
    val socketH = 3.2f * unit
    val length = r * 3.1f
    bulbs.forEachIndexed { index, bulb ->
        val color = NewYearBulbColors[bulb.colorIndex]
        val alpha = twinkleAlpha(index, elapsedMillis)
        // The bulb swings a little on its wire, each out of step with its neighbours.
        val swing = (sin(elapsedMillis / 1_900.0 * 2.0 * PI + index * 0.9) * 3.0).toFloat()
        translate(left = bulb.x, top = bulb.y) {
            rotate(degrees = swing, pivot = Offset.Zero) {
                val tip = Offset(0f, socketH + length * 0.55f)
                // Soft light spilled around the bulb.
                translate(left = tip.x, top = tip.y) {
                    drawCircle(
                        brush = geometry.glows[bulb.colorIndex],
                        radius = r * 5.5f,
                        center = Offset.Zero,
                        alpha = alpha,
                    )
                }
                // Socket: a small dark cap clipped to the wire.
                drawRoundRect(
                    color = WireDark,
                    topLeft = Offset(-r * 0.62f, -0.5f * unit),
                    size = androidx.compose.ui.geometry.Size(r * 1.24f, socketH + 0.5f * unit),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.3f),
                )
                drawRect(
                    color = WireLight.copy(alpha = 0.8f),
                    topLeft = Offset(-r * 0.62f, socketH * 0.45f),
                    size = androidx.compose.ui.geometry.Size(r * 1.24f, 0.7f * unit),
                )
                translate(top = socketH) {
                    drawPath(
                        body,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f * alpha).compositeOver(color),
                                color.copy(alpha = alpha),
                                color.copy(alpha = alpha * 0.55f),
                            ),
                            center = Offset(-r * 0.3f, length * 0.38f),
                            radius = length * 0.85f,
                        ),
                    )
                    // Glass highlight.
                    drawCircle(
                        Color.White.copy(alpha = 0.7f * alpha),
                        radius = r * 0.28f,
                        center = Offset(-r * 0.42f, length * 0.34f),
                    )
                }
            }
        }
    }
}
