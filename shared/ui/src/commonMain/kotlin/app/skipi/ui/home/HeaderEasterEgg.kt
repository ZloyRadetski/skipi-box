// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * The header logo's easter egg. Tapping it again and again makes it shake and buzz harder; after three to
 * five quick taps it blows up, a clown jumps out over the whole Home and confetti, sparks and springs fly,
 * then the clown shrinks back into the logo, which pops back into place.
 */
@Stable
class HeaderEasterEggState {
    private val clock = TimeSource.Monotonic.markNow()
    private val random = Random

    private var streak by mutableStateOf(TapBurstState(explodeAt = rollExplodeThreshold(random)))

    /** How worked up the logo is right now, 0 to 1. */
    var intensity by mutableFloatStateOf(0f)
        private set

    /** Raised by every tap that does not blow the logo up; effects restart from it. */
    var tapCount by mutableIntStateOf(0)
        private set

    /** Raised each time the logo blows up. */
    var showCount by mutableIntStateOf(0)
        private set

    var exploding by mutableStateOf(false)
        internal set

    /** Seconds into the show. */
    var seconds by mutableFloatStateOf(0f)
        internal set

    internal var particles: List<Particle> = emptyList()

    /** The big stickers; they are drawn over the clown, the small pieces behind it. */
    internal var props: List<Particle> = emptyList()
        internal set

    /** The logo's middle in window coordinates, in pixels. */
    var iconCenter by mutableStateOf(Offset.Unspecified)
        internal set

    private fun nowMillis(): Long = clock.elapsedNow().inWholeMilliseconds

    fun onTap() {
        if (exploding) return
        val now = nowMillis()
        val result = streak.afterTap(now) { rollExplodeThreshold(random) }
        streak = result.state
        if (result.exploded) {
            intensity = 1f
            val show = easterEggParticles(random)
            particles = show.filter { !it.kind.isProp }
            props = show.filter { it.kind.isProp }
            seconds = 0f
            exploding = true
            showCount++
        } else {
            intensity = streak.intensity(now)
            tapCount++
        }
    }

    /** Lets the shaking die down once the taps have stopped for a while. */
    fun calmDownIfIdle() {
        intensity = streak.intensity(nowMillis())
    }

    internal fun settleShaking() {
        intensity = 0f
    }

    internal fun finishShow() {
        exploding = false
        seconds = 0f
        intensity = 0f
    }
}

@Composable
fun rememberHeaderEasterEggState(): HeaderEasterEggState {
    val state = remember { HeaderEasterEggState() }
    val haptic = LocalHapticFeedback.current

    // The buzz grows with the shaking: one pulse per tap, and a steady rattle once the logo is close to blowing.
    LaunchedEffect(state.tapCount) {
        if (state.tapCount > 0) {
            // Always the strong click: the light tick is barely felt on a phone, and the streak is only a few taps.
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (state.intensity > 0.5f) {
                delay(60L)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            delay(TapStreakWindowMillis + 50L)
            state.calmDownIfIdle()
        }
    }
    val rattling = state.intensity >= 0.45f && !state.exploding
    LaunchedEffect(rattling) {
        while (rattling) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay((260f - 160f * state.intensity).toLong().coerceAtLeast(100L))
        }
    }

    LaunchedEffect(state.showCount) {
        if (state.showCount == 0) return@LaunchedEffect
        val progress = Animatable(0f)
        launch { playHapticBursts(haptic) }
        launch {
            // The logo keeps shaking while it shrinks away, then settles so it pops back still.
            delay(200L)
            state.settleShaking()
        }
        progress.animateTo(
            targetValue = EasterEggTimeline.TotalSeconds,
            animationSpec = tween((EasterEggTimeline.TotalSeconds * 1000).toInt(), easing = LinearEasing),
        ) { state.seconds = value }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        state.finishShow()
    }
    return state
}

/** The blast, the clown landing and the logo's return are each felt as a short run of pulses. */
private suspend fun playHapticBursts(haptic: HapticFeedback) {
    repeat(6) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(65L)
    }
    delay(400L)
    repeat(4) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(75L)
    }
}

/** Shakes, pulses and hides the logo as the easter egg asks, and reports where it is for the blast. */
internal fun Modifier.easterEggLogo(state: HeaderEasterEggState): Modifier = composed {
    val shown by animateFloatAsState(state.intensity, tween(160), label = "easter_egg_intensity")
    var phase by remember { mutableFloatStateOf(0f) }
    val shaking = shown > 0.01f && !state.exploding
    LaunchedEffect(shaking) {
        // A frame clock runs only while the logo shakes, so a quiet header costs nothing.
        var last = 0L
        while (shaking) {
            withFrameNanos { now ->
                if (last != 0L) phase += (now - last) / 1_000_000_000f * (16f + 36f * shown)
                last = now
            }
        }
    }
    val density = LocalDensity.current
    this
        .onGloballyPositioned { coordinates ->
            state.iconCenter = coordinates.positionInRoot() + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
        }
        .graphicsLayer {
            val power = shown * shown
            val swing = sin(phase * 2f * PI.toFloat())
            rotationZ = swing * 14f * power
            translationX = sin(phase * 7.3f + 1f) * 3.2f * density.density * power
            translationY = cos(phase * 5.1f) * 2.2f * density.density * power
            val pulse = 1f + 0.12f * power * (0.5f + 0.5f * sin(phase * 3.7f * PI.toFloat()))
            val showScale = if (state.exploding) EasterEggTimeline.iconScale(state.seconds) else 1f
            scaleX = pulse * showScale
            scaleY = pulse * showScale
        }
}

private val Palette = listOf(
    Color(0xFFFF5A5F), Color(0xFFFFD23F), Color(0xFF3DDC97), Color(0xFF3FA7FF),
    Color(0xFFA66BFF), Color(0xFFFF6FB5), Color(0xFFFF9F1C), Color(0xFFBAF955),
)

/**
 * The show itself, laid over the whole Home: a dim, a ring and a flash where the logo was, the particles
 * and the clown. It takes every touch while it plays.
 */
@Composable
internal fun HeaderEasterEggOverlay(state: HeaderEasterEggState, modifier: Modifier = Modifier) {
    if (!state.exploding) return
    val density = LocalDensity.current.density
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val seconds = state.seconds
        val particles = state.particles
        EasterEggCanvas(state, seconds, particles, overlayOrigin, density)
    }
}

@Composable
private fun EasterEggCanvas(
    state: HeaderEasterEggState,
    seconds: Float,
    particles: List<Particle>,
    overlayOrigin: Offset,
    density: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val icon = if (state.iconCenter.isSpecified) state.iconCenter - overlayOrigin else Offset(size.width * 0.1f, 40f * density)

        drawRect(Color.Black.copy(alpha = EasterEggTimeline.scrimAlpha(seconds)))
        drawShockwave(icon, seconds, density)
        drawParticles(particles, seconds, icon, center, density)
        drawClownAt(icon, center, seconds)
        drawProps(state.props, seconds, center, density)
    }
}

private fun DrawScope.drawShockwave(icon: Offset, seconds: Float, density: Float) {
    if (seconds > 0.9f) return
    val reach = max(size.width, size.height)
    for (ring in 0..2) {
        val t = ((seconds - ring * 0.09f) / 0.7f).coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) continue
        val radius = reach * (1f - (1f - t) * (1f - t))
        drawCircle(
            color = Palette[(ring * 3 + 1) % Palette.size].copy(alpha = (1f - t) * 0.55f),
            radius = radius,
            center = icon,
            style = Stroke(width = (10f - 6f * t) * density),
        )
    }
    val flash = (1f - seconds / 0.22f).coerceIn(0f, 1f)
    if (flash > 0f) {
        drawCircle(Color.White.copy(alpha = flash * 0.9f), radius = 70f * density * (1f + (1f - flash)), center = icon)
    }
}

private fun DrawScope.drawParticles(particles: List<Particle>, seconds: Float, icon: Offset, center: Offset, density: Float) {
    for (particle in particles) {
        if (!particle.isAliveAt(seconds)) continue
        val anchor = if (particle.anchor == ParticleAnchor.Icon) icon else center
        val at = Offset(anchor.x + particle.xAt(seconds) * density, anchor.y + particle.yAt(seconds) * density)
        if (at.x < -80f || at.x > size.width + 80f || at.y > size.height + 80f) continue
        val life = particle.lifeFractionAt(seconds)
        val fade = if (life > 0.72f) (1f - life) / 0.28f else 1f
        val age = seconds - particle.spawnSeconds
        val color = Palette[particle.colorIndex % Palette.size]
        val px = particle.sizeDp * density
        val angle = particle.rotation0 + particle.spin * age
        when (particle.kind) {
            ParticleKind.Confetti -> {
                val flip = abs(cos(particle.phase + age * 7f)).coerceAtLeast(0.12f)
                withTransform({
                    translate(at.x, at.y)
                    rotate(angle, Offset.Zero)
                    scale(1f, flip, Offset.Zero)
                }) {
                    drawRect(color.copy(alpha = fade), Offset(-px / 2f, -px / 4f), Size(px, px / 2f))
                }
            }

            ParticleKind.Spark -> {
                val twinkle = 0.55f + 0.45f * sin(age * 16f + particle.phase)
                withTransform({
                    translate(at.x, at.y)
                    rotate(angle * 0.4f, Offset.Zero)
                }) {
                    drawPath(sparklePath(px * (0.7f + 0.3f * twinkle)), Color.White.copy(alpha = fade * twinkle))
                    drawCircle(color.copy(alpha = fade * 0.35f * twinkle), radius = px * 0.8f)
                }
            }

            ParticleKind.Spring -> {
                val boing = 0.55f + 0.45f * sin(age * 11f + particle.phase)
                withTransform({
                    translate(at.x, at.y)
                    rotate(angle, Offset.Zero)
                }) {
                    drawPath(
                        path = springPath(length = px * (0.9f + 0.7f * boing), amplitude = px * 0.3f),
                        color = color.copy(alpha = fade),
                        style = Stroke(width = max(2f, px * 0.13f), cap = StrokeCap.Round),
                    )
                }
            }

            ParticleKind.Dot -> drawCircle(color.copy(alpha = fade), radius = px / 2f, center = at)

            // The props are stickers, drawn over the clown.
            else -> Unit
        }
    }
}

/** A four-pointed glitter star of the given [radius], centred on the origin. */
private fun sparklePath(radius: Float): Path = Path().apply {
    val inner = radius * 0.22f
    for (point in 0 until 8) {
        val r = if (point % 2 == 0) radius else inner
        val a = point * PI.toFloat() / 4f - PI.toFloat() / 2f
        val x = cos(a) * r
        val y = sin(a) * r
        if (point == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A coiled spring lying along the x axis, centred on the origin. */
private fun springPath(length: Float, amplitude: Float): Path = Path().apply {
    val coils = 4
    val steps = coils * 12
    for (step in 0..steps) {
        val f = step / steps.toFloat()
        val x = (f - 0.5f) * length
        val y = sin(f * coils * 2f * PI.toFloat()) * amplitude
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
}
