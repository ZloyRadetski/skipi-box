// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Taps on the logo count as one streak while each comes within this time of the one before. */
internal const val TapStreakWindowMillis = 1_200L

private const val MinExplodeTaps = 3
private const val MaxExplodeTaps = 5

/** How many taps a new streak takes before the logo blows up: three to five, few enough that it gets found. */
internal fun rollExplodeThreshold(random: Random): Int = random.nextInt(MinExplodeTaps, MaxExplodeTaps + 1)

internal data class TapBurstState(
    val count: Int = 0,
    val lastTapMillis: Long = 0L,
    val explodeAt: Int = MaxExplodeTaps,
)

internal data class TapBurstResult(val state: TapBurstState, val exploded: Boolean)

/** The streak after a tap at [nowMillis]; a tap after a pause, or the one that blows the logo up, starts a fresh one. */
internal fun TapBurstState.afterTap(nowMillis: Long, rollExplodeAt: () -> Int): TapBurstResult {
    val streak = if (count > 0 && nowMillis - lastTapMillis <= TapStreakWindowMillis) {
        this
    } else {
        TapBurstState(explodeAt = rollExplodeAt())
    }
    val taps = streak.count + 1
    return if (taps >= streak.explodeAt) {
        TapBurstResult(TapBurstState(explodeAt = rollExplodeAt()), exploded = true)
    } else {
        TapBurstResult(streak.copy(count = taps, lastTapMillis = nowMillis), exploded = false)
    }
}

/** How worked up the logo is, from 0 (calm) to 1 (about to blow); it drops to 0 once the taps stop. */
internal fun TapBurstState.intensity(nowMillis: Long): Float {
    if (count == 0 || nowMillis - lastTapMillis > TapStreakWindowMillis) return 0f
    return (count.toFloat() / explodeAt).coerceIn(0f, 1f)
}

private const val BackOvershoot = 1.70158f

internal fun easeOutBack(x: Float): Float {
    val c3 = BackOvershoot + 1f
    val t = x.coerceIn(0f, 1f) - 1f
    return 1f + c3 * t * t * t + BackOvershoot * t * t
}

private fun easeInBack(x: Float): Float {
    val c3 = BackOvershoot + 1f
    val t = x.coerceIn(0f, 1f)
    return c3 * t * t * t - BackOvershoot * t * t
}

private fun easeInOutCubic(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

/**
 * The show's clock. It begins and ends on the quiet header: the logo bursts, the clown flies out, and at the
 * end shrinks back into the logo, which pops back into place, so the animation can start again from nothing.
 */
internal object EasterEggTimeline {
    const val TotalSeconds = 4.2f

    private const val IconVanishSeconds = 0.12f
    private const val ClownInStart = 0.12f
    private const val ClownInEnd = 0.72f
    private const val ClownOutStart = TotalSeconds - 0.8f
    private const val ClownOutEnd = TotalSeconds - 0.1f
    private const val IconReturnSeconds = 0.35f
    private const val ScrimInSeconds = 0.35f
    private const val ScrimOutSeconds = 0.7f
    private const val ScrimPeak = 0.62f

    private fun Float.inShow() = coerceIn(0f, TotalSeconds)

    /** Size of the logo on the header: it shrinks away as it blows up and pops back at the end. */
    fun iconScale(seconds: Float): Float {
        val t = seconds.inShow()
        return when {
            t <= 0f -> 1f
            t < IconVanishSeconds -> 1f - easeInOutCubic(t / IconVanishSeconds)
            t < TotalSeconds - IconReturnSeconds -> 0f
            t < TotalSeconds -> easeOutBack((t - (TotalSeconds - IconReturnSeconds)) / IconReturnSeconds).coerceAtLeast(0f)
            else -> 1f
        }
    }

    /** Size of the clown, from nothing, past full size with a bounce, back to nothing inside the logo. */
    fun clownScale(seconds: Float): Float {
        val t = seconds.inShow()
        return when {
            t <= ClownInStart -> 0f
            t < ClownInEnd -> easeOutBack((t - ClownInStart) / (ClownInEnd - ClownInStart)).coerceAtLeast(0f)
            t < ClownOutStart -> 1f
            t < ClownOutEnd -> (1f - easeInBack((t - ClownOutStart) / (ClownOutEnd - ClownOutStart))).coerceAtLeast(0f)
            else -> 0f
        }
    }

    /** Where the clown is between the logo (0) and the middle of the screen (1). */
    fun clownTravel(seconds: Float): Float {
        val t = seconds.inShow()
        return when {
            t <= ClownInStart -> 0f
            t < ClownInEnd -> easeInOutCubic((t - ClownInStart) / (ClownInEnd - ClownInStart))
            t < ClownOutStart -> 1f
            t < ClownOutEnd -> 1f - easeInOutCubic((t - ClownOutStart) / (ClownOutEnd - ClownOutStart))
            else -> 0f
        }
    }

    /** How dark the screen behind the clown is. */
    fun scrimAlpha(seconds: Float): Float {
        val t = seconds.inShow()
        return when {
            t <= 0f -> 0f
            t < ScrimInSeconds -> ScrimPeak * easeInOutCubic(t / ScrimInSeconds)
            t < TotalSeconds - ScrimOutSeconds -> ScrimPeak
            t < TotalSeconds -> ScrimPeak * (1f - easeInOutCubic((t - (TotalSeconds - ScrimOutSeconds)) / ScrimOutSeconds))
            else -> 0f
        }
    }
}

/** The small pieces fly in the blast; the props are big stickers launched from below, as in a chat's emoji effects. */
internal enum class ParticleKind(val isProp: Boolean = false) {
    Confetti, Spark, Spring, Dot,
    Balloon(isProp = true), BeachBall(isProp = true), BowTie(isProp = true), Flower(isProp = true), Drum(isProp = true),
}

/** Where a particle's own coordinates start from: the logo on the header or the middle of the screen. */
internal enum class ParticleAnchor { Icon, Center }

private const val Gravity = 900f
private const val Drag = 1.6f

/** One piece of the fireworks. Positions are in dp from its anchor and are computed from the time alone. */
internal data class Particle(
    val kind: ParticleKind,
    val originX: Float,
    val originY: Float,
    val vx: Float,
    val vy: Float,
    val spawnSeconds: Float,
    val lifeSeconds: Float,
    val sizeDp: Float,
    val rotation0: Float,
    /** Degrees per second. */
    val spin: Float,
    val colorIndex: Int,
    /** Offsets the twinkle and the flutter so the pieces do not move in step. */
    val phase: Float,
    val anchor: ParticleAnchor = ParticleAnchor.Icon,
    /** How much of the gravity pulls on it: a balloon is light, everything else is not. */
    val gravityScale: Float = 1f,
) {
    fun isAliveAt(seconds: Float): Boolean = seconds >= spawnSeconds && seconds <= spawnSeconds + lifeSeconds

    /** 0 at birth, 1 at the end of its life. */
    fun lifeFractionAt(seconds: Float): Float = ((seconds - spawnSeconds) / lifeSeconds).coerceIn(0f, 1f)

    private fun flown(seconds: Float): Float = (1f - exp(-Drag * (seconds - spawnSeconds))) / Drag

    fun xAt(seconds: Float): Float = originX + vx * flown(seconds)

    fun yAt(seconds: Float): Float {
        val dt = seconds - spawnSeconds
        return originY + vy * flown(seconds) + 0.5f * Gravity * gravityScale * dt * dt
    }
}

private fun Random.range(from: Float, until: Float): Float = from + nextFloat() * (until - from)

private const val ConfettiShare = 0.45f
private const val SparkShare = 0.65f
private const val SpringShare = 0.80f

internal const val EasterEggPaletteSize = 8

/** A burst of [count] particles flying out in every direction; [power] scales how fast and far. */
internal fun generateBurst(
    random: Random,
    originX: Float,
    originY: Float,
    count: Int,
    spawnSeconds: Float,
    power: Float,
    anchor: ParticleAnchor = ParticleAnchor.Icon,
): List<Particle> = List(count) {
    val angle = random.nextFloat() * 2f * PI.toFloat()
    val speed = power * (250f + 900f * random.nextFloat().pow(0.7f))
    val roll = random.nextFloat()
    val kind = when {
        roll < ConfettiShare -> ParticleKind.Confetti
        roll < SparkShare -> ParticleKind.Spark
        roll < SpringShare -> ParticleKind.Spring
        else -> ParticleKind.Dot
    }
    val born = spawnSeconds + random.range(0f, 0.12f)
    Particle(
        kind = kind,
        originX = originX,
        originY = originY,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed - 220f * power,
        spawnSeconds = born,
        lifeSeconds = min(random.range(1.6f, 2.8f), EasterEggTimeline.TotalSeconds - born),
        sizeDp = when (kind) {
            ParticleKind.Confetti -> random.range(7f, 13f)
            ParticleKind.Spark -> random.range(10f, 20f)
            ParticleKind.Spring -> random.range(18f, 30f)
            else -> random.range(4f, 8f)
        },
        rotation0 = random.range(0f, 360f),
        spin = random.range(-540f, 540f),
        colorIndex = random.nextInt(EasterEggPaletteSize),
        phase = random.range(0f, 2f * PI.toFloat()),
        anchor = anchor,
    )
}

private const val RainStartY = -520f
private const val RainHalfWidth = 230f

/** Confetti, glitter and springs falling from above the top edge, spawned evenly between the two times. */
internal fun generateRain(random: Random, count: Int, fromSeconds: Float, untilSeconds: Float): List<Particle> =
    List(count) { index ->
        val roll = random.nextFloat()
        val kind = when {
            roll < 0.6f -> ParticleKind.Confetti
            roll < 0.75f -> ParticleKind.Spark
            roll < 0.85f -> ParticleKind.Spring
            else -> ParticleKind.Dot
        }
        val born = fromSeconds + (untilSeconds - fromSeconds) * (index + random.nextFloat()) / count
        Particle(
            kind = kind,
            originX = random.range(-RainHalfWidth, RainHalfWidth),
            originY = RainStartY,
            vx = random.range(-60f, 60f),
            vy = random.range(60f, 260f),
            spawnSeconds = born,
            lifeSeconds = min(random.range(1.8f, 2.3f), EasterEggTimeline.TotalSeconds - born),
            sizeDp = when (kind) {
                ParticleKind.Confetti -> random.range(7f, 12f)
                ParticleKind.Spark -> random.range(9f, 16f)
                ParticleKind.Spring -> random.range(16f, 26f)
                else -> random.range(4f, 7f)
            },
            rotation0 = random.range(0f, 360f),
            spin = random.range(-360f, 360f),
            colorIndex = random.nextInt(EasterEggPaletteSize),
            phase = random.range(0f, 2f * PI.toFloat()),
            anchor = ParticleAnchor.Center,
        )
    }

/** Every particle of the show: a blast from the logo, a second one as the clown lands, then glitter and a confetti rain. */
internal fun easterEggParticles(random: Random): List<Particle> =
    generateBurst(random, 0f, 0f, count = 90, spawnSeconds = 0.08f, power = 1f, anchor = ParticleAnchor.Icon) +
        generateBurst(random, 0f, 0f, count = 60, spawnSeconds = 0.62f, power = 1.25f, anchor = ParticleAnchor.Center) +
        generateBurst(random, 0f, 0f, count = 30, spawnSeconds = 1.3f, power = 0.7f, anchor = ParticleAnchor.Center) +
        generateRain(random, count = 150, fromSeconds = 0.9f, untilSeconds = 2.4f) +
        generateProps(random, count = 11, fromSeconds = 0.55f, untilSeconds = 2.1f)

private val PropKinds = ParticleKind.entries.filter { it.isProp }
private const val PropStartY = 520f

/**
 * Big stickers (balloons, a beach ball, a bow tie, a flower, a drum) shot up from below the bottom edge,
 * as if thrown into the air, that arc over and fall back; balloons are light and hang around longer.
 */
internal fun generateProps(random: Random, count: Int, fromSeconds: Float, untilSeconds: Float): List<Particle> {
    val firstKind = random.nextInt(PropKinds.size)
    return List(count) { index ->
        val kind = PropKinds[(firstKind + index) % PropKinds.size]
        val born = fromSeconds + (untilSeconds - fromSeconds) * (index + random.nextFloat()) / count
        val x = random.range(-190f, 190f)
        Particle(
            kind = kind,
            originX = x,
            originY = PropStartY,
            vx = -x * 0.35f + random.range(-120f, 120f),
            vy = if (kind == ParticleKind.Balloon) -random.range(900f, 1300f) else -random.range(1800f, 2300f),
            spawnSeconds = born,
            lifeSeconds = min(random.range(2.0f, 2.8f), EasterEggTimeline.TotalSeconds - born),
            sizeDp = when (kind) {
                ParticleKind.Balloon -> random.range(70f, 105f)
                ParticleKind.BeachBall -> random.range(60f, 95f)
                ParticleKind.BowTie -> random.range(80f, 115f)
                ParticleKind.Flower -> random.range(60f, 88f)
                else -> random.range(70f, 105f)
            },
            rotation0 = random.range(-25f, 25f),
            spin = random.range(-140f, 140f),
            colorIndex = random.nextInt(EasterEggPaletteSize),
            phase = random.range(0f, 2f * PI.toFloat()),
            anchor = ParticleAnchor.Center,
            gravityScale = if (kind == ParticleKind.Balloon) 0.25f else 1f,
        )
    }
}
