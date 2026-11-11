// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The artwork of the easter egg, drawn with paths so it looks the same on every platform.

private val Ink = Color(0xFF2B2B2B)

/** The clown blinks twice while it holds the screen. */
private fun blinkAt(seconds: Float): Float {
    val closed = listOf(1.35f, 2.35f).any { start -> seconds in start..(start + 0.14f) }
    return if (closed) 0.12f else 1f
}

/** The nose squashes for a moment, twice, as if the clown were honked. */
private fun honkAt(seconds: Float): Float {
    val honk = listOf(1.7f, 2.65f).firstOrNull { start -> seconds in start..(start + 0.22f) } ?: return 0f
    return sin((seconds - honk) / 0.22f * PI.toFloat())
}

/** The clown at its place on the way between the [icon] and the [center] of the screen. */
internal fun DrawScope.drawClownAt(icon: Offset, center: Offset, seconds: Float) {
    val scale = EasterEggTimeline.clownScale(seconds)
    if (scale <= 0f) return
    val travel = EasterEggTimeline.clownTravel(seconds)
    val position = Offset(icon.x + (center.x - icon.x) * travel, icon.y + (center.y - icon.y) * travel)
    val side = min(size.width, size.height) * 0.9f
    // It tumbles in, then bobs and sways while it stays, and straightens up to fly back into the logo.
    val sway = sin(seconds * 6.2f) * 5f * travel
    val breathe = 1f + 0.035f * sin(seconds * 9.5f) * travel
    val tumble = (1f - travel) * 340f
    withTransform({
        translate(position.x, position.y)
        rotate(sway + tumble, Offset.Zero)
        scale(scale * breathe, scale * breathe, Offset.Zero)
        translate(-side / 2f, -side / 2f)
    }) {
        drawClown(side, blink = blinkAt(seconds), honk = honkAt(seconds))
    }
}

private val Cream = Color(0xFFFFF3D6)
private val CreamShade = Color(0xFFEFD9A6)
private val HairRed = Color(0xFFE8362B)
private val HairShade = Color(0xFFB5231B)
private val NoseRed = Color(0xFFE6261B)
private val EyeBlue = Color(0xFF2C6BD1)
private val MouthRed = Color(0xFFD7221A)
private val MouthDark = Color(0xFF8E1410)

/** The round clown face of the Noto emoji: red curls, blue-rimmed eyes, red nose and a big round grin, in a square of [side] pixels. */
private fun DrawScope.drawClown(side: Float, blink: Float = 1f, honk: Float = 0f) {
    val u = side / 100f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)

    // Curly red hair on both sides of the head.
    val curls = listOf(
        Triple(15f, 30f, 9f), Triple(22f, 21f, 9.5f), Triple(31f, 15f, 8.5f),
        Triple(10f, 42f, 8.5f), Triple(14f, 54f, 7f),
    )
    for ((x, y, r) in curls) {
        for (mirror in listOf(false, true)) {
            val cx = if (mirror) 100f - x else x
            drawCircle(HairShade, (r + 0.9f) * u, p(cx, y))
            drawCircle(HairRed, r * u, p(cx, y))
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.32f * u, p(cx + (if (mirror) 2f else -2f), y - r * 0.4f))
        }
    }

    // The face, with a slightly darker rim to keep it round.
    drawCircle(CreamShade, 38.5f * u, p(50f, 54f))
    drawCircle(Cream, 36.5f * u, p(50f, 53.5f))

    // Rosy cheeks.
    drawCircle(Color(0xFFF26A5F).copy(alpha = 0.5f), 9f * u, p(24f, 64f))
    drawCircle(Color(0xFFF26A5F).copy(alpha = 0.5f), 9f * u, p(76f, 64f))

    // Eyebrows.
    for (x in listOf(36f, 64f)) {
        val brow = Path().apply {
            moveTo(p(x - 9f, 31f).x, p(x - 9f, 31f).y)
            quadraticTo(p(x, 24.5f).x, p(x, 24.5f).y, p(x + 9f, 31f).x, p(x + 9f, 31f).y)
        }
        drawPath(brow, Ink, style = Stroke(width = 1.7f * u, cap = StrokeCap.Round))
    }

    // Tall eyes with a thick blue rim and a dark pupil; they squash flat when the clown blinks.
    for (x in listOf(36f, 64f)) {
        val ry = 12f * blink
        drawOval(Color.White, p(x - 8.5f, 47f - ry), Size(17f * u, 2f * ry * u))
        drawOval(EyeBlue, p(x - 8.5f, 47f - ry), Size(17f * u, 2f * ry * u), style = Stroke(width = 2.6f * u))
        if (blink > 0.5f) {
            drawOval(Ink, p(x - 2.8f, 47f - 5.4f), Size(5.6f * u, 10.8f * u))
            drawCircle(Color.White.copy(alpha = 0.8f), 0.9f * u, p(x - 1.2f, 43f))
        }
    }

    // Nose; it squashes wide and flat when honked.
    val noseW = 6.6f * (1f + 0.35f * honk)
    val noseH = 6.6f * (1f - 0.3f * honk)
    drawOval(NoseRed, p(50f - noseW, 63f - noseH), Size(2f * noseW * u, 2f * noseH * u))
    drawCircle(Color.White.copy(alpha = 0.55f), 1.9f * u, p(47.8f, 60.6f))

    // A big round grin: a bowl with a gently curved top, a band of teeth under the top edge and a tongue at the bottom.
    val grin = Path().apply {
        moveTo(p(29f, 68f).x, p(29f, 68f).y)
        quadraticTo(p(50f, 72.5f).x, p(50f, 72.5f).y, p(71f, 68f).x, p(71f, 68f).y)
        cubicTo(p(72f, 91.5f).x, p(72f, 91.5f).y, p(28f, 91.5f).x, p(28f, 91.5f).y, p(29f, 68f).x, p(29f, 68f).y)
        close()
    }
    drawPath(grin, MouthRed)
    clipPath(grin) {
        // The tongue, then the teeth over the top of the mouth.
        drawOval(Color(0xFFF2625A), p(40f, 79.5f), Size(20f * u, 11f * u))
        val teeth = Path().apply {
            moveTo(p(29f, 68f).x, p(29f, 68f).y)
            quadraticTo(p(50f, 72.5f).x, p(50f, 72.5f).y, p(71f, 68f).x, p(71f, 68f).y)
            lineTo(p(71f, 74.5f).x, p(71f, 74.5f).y)
            quadraticTo(p(50f, 79f).x, p(50f, 79f).y, p(29f, 74.5f).x, p(29f, 74.5f).y)
            close()
        }
        drawPath(teeth, Color.White)
    }
    drawPath(grin, MouthDark, style = Stroke(width = 1.2f * u, join = StrokeJoin.Round))
}

private val PropPalette = listOf(
    Color(0xFF3FB6FF), Color(0xFFFF5A8A), Color(0xFFFFC93C), Color(0xFF45D483),
    Color(0xFFA66BFF), Color(0xFFFF7A3C), Color(0xFF28C7C7), Color(0xFFFF4D4D),
)

/** The big stickers, drawn over the clown: each pops in with a bounce, wobbles on its way and fades out. */
internal fun DrawScope.drawProps(props: List<Particle>, seconds: Float, center: Offset, density: Float) {
    for (prop in props) {
        if (!prop.isAliveAt(seconds)) continue
        val age = seconds - prop.spawnSeconds
        val at = Offset(center.x + prop.xAt(seconds) * density, center.y + prop.yAt(seconds) * density)
        val px = prop.sizeDp * density
        if (at.x < -px || at.x > size.width + px || at.y < -px * 2f || at.y > size.height + px) continue
        val life = prop.lifeFractionAt(seconds)
        val fade = if (life > 0.8f) (1f - life) / 0.2f else 1f
        val pop = easeOutBack(age / 0.28f).coerceAtLeast(0f)
        val angle = prop.rotation0 + prop.spin * age * 0.4f + 9f * sin(age * 4.2f + prop.phase)
        withTransform({
            translate(at.x, at.y)
            rotate(angle, Offset.Zero)
            scale(pop, pop, Offset.Zero)
        }) {
            drawProp(prop.kind, px, PropPalette[prop.colorIndex % PropPalette.size], fade)
        }
    }
}

private fun DrawScope.drawProp(kind: ParticleKind, s: Float, color: Color, alpha: Float) {
    when (kind) {
        ParticleKind.Balloon -> drawBalloon(s, color, alpha)
        ParticleKind.BeachBall -> drawBeachBall(s, alpha)
        ParticleKind.BowTie -> drawBowTie(s, alpha)
        ParticleKind.Flower -> drawFlower(s, alpha)
        ParticleKind.Drum -> drawDrum(s, alpha)
        else -> Unit
    }
}

private fun DrawScope.drawBalloon(s: Float, color: Color, alpha: Float) {
    val string = Path().apply {
        moveTo(0f, 0.43f * s)
        cubicTo(-0.12f * s, 0.6f * s, 0.12f * s, 0.75f * s, 0f, 0.95f * s)
    }
    drawPath(string, Color.White.copy(alpha = 0.7f * alpha), style = Stroke(width = max(2f, 0.02f * s), cap = StrokeCap.Round))
    val knot = Path().apply {
        moveTo(0f, 0.38f * s)
        lineTo(-0.05f * s, 0.47f * s)
        lineTo(0.05f * s, 0.47f * s)
        close()
    }
    drawPath(knot, color.copy(alpha = alpha))
    drawOval(color.copy(alpha = alpha), Offset(-0.34f * s, -0.5f * s), Size(0.68f * s, 0.9f * s))
    drawOval(Color.White.copy(alpha = 0.55f * alpha), Offset(-0.2f * s, -0.38f * s), Size(0.15f * s, 0.26f * s))
}

private fun DrawScope.drawBeachBall(s: Float, alpha: Float) {
    val r = s * 0.45f
    val colors = listOf(Color(0xFF3FB6FF), Color.White, Color(0xFFFFD23F), Color(0xFFFF5A8A), Color.White, Color(0xFFFFD23F))
    colors.forEachIndexed { index, c ->
        drawArc(
            color = c.copy(alpha = alpha),
            startAngle = index * 60f,
            sweepAngle = 60.5f,
            useCenter = true,
            topLeft = Offset(-r, -r),
            size = Size(2f * r, 2f * r),
        )
    }
    drawCircle(Color.White.copy(alpha = alpha), r * 0.18f, Offset.Zero)
    drawCircle(Color.Black.copy(alpha = 0.12f * alpha), r, Offset.Zero, style = Stroke(width = max(1.5f, 0.02f * s)))
}

private fun DrawScope.drawBowTie(s: Float, alpha: Float) {
    val yellow = Color(0xFFFFD23F).copy(alpha = alpha)
    val edge = Color(0xFFE59A12).copy(alpha = alpha)
    for (side in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(0f, 0f)
            lineTo(side * 0.5f * s, -0.27f * s)
            quadraticTo(side * 0.56f * s, 0f, side * 0.5f * s, 0.27f * s)
            close()
        }
        drawPath(wing, yellow)
        drawPath(wing, edge, style = Stroke(width = max(1.5f, 0.02f * s)))
        drawCircle(Color(0xFFFF5A8A).copy(alpha = alpha), 0.04f * s, Offset(side * 0.3f * s, -0.07f * s))
        drawCircle(Color(0xFF3FB6FF).copy(alpha = alpha), 0.04f * s, Offset(side * 0.38f * s, 0.09f * s))
        drawCircle(Color(0xFF45D483).copy(alpha = alpha), 0.035f * s, Offset(side * 0.2f * s, 0.07f * s))
    }
    drawRoundRect(Color(0xFFFFC21F).copy(alpha = alpha), Offset(-0.07f * s, -0.1f * s), Size(0.14f * s, 0.2f * s), CornerRadius(0.04f * s))
    drawRoundRect(edge, Offset(-0.07f * s, -0.1f * s), Size(0.14f * s, 0.2f * s), CornerRadius(0.04f * s), style = Stroke(width = max(1.5f, 0.02f * s)))
}

private fun DrawScope.drawFlower(s: Float, alpha: Float) {
    val red = Color(0xFFE8362B).copy(alpha = alpha)
    val shade = Color(0xFFB5231B).copy(alpha = alpha)
    for (petal in 0 until 6) {
        val a = petal * PI.toFloat() / 3f
        val c = Offset(cos(a) * 0.27f * s, sin(a) * 0.27f * s)
        drawCircle(shade, 0.2f * s, c)
        drawCircle(red, 0.185f * s, c)
    }
    drawCircle(Color(0xFFFFC21F).copy(alpha = alpha), 0.16f * s, Offset.Zero)
    drawCircle(Color(0xFFFF9F1C).copy(alpha = alpha), 0.09f * s, Offset.Zero)
}

private fun DrawScope.drawDrum(s: Float, alpha: Float) {
    val w = 0.8f * s
    val h = 0.5f * s
    val red = Color(0xFFE8362B).copy(alpha = alpha)
    val gold = Color(0xFFFFC21F).copy(alpha = alpha)
    val bodyTop = -0.1f * s
    drawRoundRect(red, Offset(-w / 2f, bodyTop), Size(w, h), CornerRadius(0.06f * s))
    for (stripe in 0 until 5) {
        val x = -w / 2f + (stripe + 0.5f) * w / 5f
        val zig = Path().apply {
            moveTo(x - 0.08f * s, bodyTop + 0.03f * s)
            lineTo(x + 0.08f * s, bodyTop + 0.03f * s)
            lineTo(x, bodyTop + h - 0.03f * s)
            close()
        }
        drawPath(zig, Color.White.copy(alpha = alpha))
    }
    drawRoundRect(gold, Offset(-w / 2f, bodyTop + h - 0.06f * s), Size(w, 0.06f * s), CornerRadius(0.03f * s))
    drawOval(Color(0xFFFFF3D6).copy(alpha = alpha), Offset(-w / 2f, bodyTop - 0.12f * s), Size(w, 0.24f * s))
    drawOval(gold, Offset(-w / 2f, bodyTop - 0.12f * s), Size(w, 0.24f * s), style = Stroke(width = 0.035f * s))
}
