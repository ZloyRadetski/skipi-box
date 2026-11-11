// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TapBurstTest {
    private fun roll(value: Int): () -> Int = { value }

    @Test
    fun theTapThatReachesTheThresholdExplodesAndStartsAFreshStreak() {
        var state = TapBurstState()
        var exploded = false
        for (tap in 1..6) {
            val result = state.afterTap(nowMillis = tap * 100L, rollExplodeAt = roll(6))
            state = result.state
            exploded = result.exploded
            assertEquals(tap == 6, exploded, "tap $tap")
        }
        assertEquals(0, state.count)
    }

    @Test
    fun aPauseLongerThanTheWindowStartsTheCountOver() {
        var state = TapBurstState()
        repeat(3) { tap -> state = state.afterTap(tap * 100L, roll(8)).state }
        assertEquals(3, state.count)

        state = state.afterTap(nowMillis = 200L + TapStreakWindowMillis + 1, rollExplodeAt = roll(8)).state

        assertEquals(1, state.count)
    }

    @Test
    fun tapsInsideTheWindowKeepTheStreakAlive() {
        var state = TapBurstState()
        var now = 0L
        repeat(5) {
            state = state.afterTap(now, roll(10)).state
            now += TapStreakWindowMillis
        }
        assertEquals(5, state.count)
    }

    @Test
    fun intensityGrowsWithEveryTapAndFadesWhenTheTapsStop() {
        var state = TapBurstState()
        var previous = 0f
        for (tap in 1..4) {
            state = state.afterTap(tap * 100L, roll(8)).state
            val now = state.intensity(tap * 100L)
            assertTrue(now > previous, "intensity must grow at tap $tap")
            previous = now
        }
        assertEquals(0f, state.intensity(400L + TapStreakWindowMillis + 1))
    }

    @Test
    fun intensityNeverLeavesZeroToOne() {
        val state = TapBurstState(count = 50, lastTapMillis = 0L, explodeAt = 8)
        assertEquals(1f, state.intensity(0L))
        assertEquals(0f, TapBurstState().intensity(0L))
    }

    @Test
    fun theThresholdIsRolledBetweenFiveAndTenTaps() {
        val rolled = (1..200).map { rollExplodeThreshold(Random(it)) }
        assertTrue(rolled.all { it in 5..10 })
        assertTrue(rolled.toSet().size > 1)
    }
}

class EasterEggTimelineTest {
    private val end = EasterEggTimeline.TotalSeconds

    @Test
    fun theShowStartsAndEndsOnTheQuietHeader() {
        assertEquals(1f, EasterEggTimeline.iconScale(0f))
        assertEquals(1f, EasterEggTimeline.iconScale(end))
        assertEquals(0f, EasterEggTimeline.clownScale(0f))
        assertEquals(0f, EasterEggTimeline.clownScale(end))
        assertEquals(0f, EasterEggTimeline.clownTravel(0f))
        assertEquals(0f, EasterEggTimeline.clownTravel(end))
        assertEquals(0f, EasterEggTimeline.scrimAlpha(0f))
        assertEquals(0f, EasterEggTimeline.scrimAlpha(end))
    }

    @Test
    fun theClownIsFullSizeInTheMiddleOfTheShowAndTheIconIsGone() {
        val middle = end / 2f
        assertEquals(1f, EasterEggTimeline.clownScale(middle))
        assertEquals(1f, EasterEggTimeline.clownTravel(middle))
        assertEquals(0f, EasterEggTimeline.iconScale(middle))
        assertTrue(EasterEggTimeline.scrimAlpha(middle) > 0.4f)
    }

    @Test
    fun theIconPopsBackWhileTheClownIsShrinkingIntoIt() {
        var t = 0f
        var clownGoneAt = -1f
        var iconBackAt = -1f
        while (t <= end) {
            if (clownGoneAt < 0f && t > end / 2f && EasterEggTimeline.clownScale(t) == 0f) clownGoneAt = t
            if (iconBackAt < 0f && t > end / 2f && EasterEggTimeline.iconScale(t) > 0f) iconBackAt = t
            t += 0.01f
        }
        assertTrue(iconBackAt in 0f..end)
        assertTrue(clownGoneAt >= 0f)
        assertTrue(iconBackAt <= clownGoneAt + 0.2f, "the icon pops back as the clown shrinks into it")
    }

    @Test
    fun noValueStaysOutsideItsRangeForTooLongAndTimeOutsideTheShowIsClamped() {
        assertEquals(EasterEggTimeline.clownScale(0f), EasterEggTimeline.clownScale(-3f))
        assertEquals(EasterEggTimeline.clownScale(end), EasterEggTimeline.clownScale(end + 5f))
        var t = 0f
        while (t <= end) {
            assertTrue(EasterEggTimeline.scrimAlpha(t) in 0f..1f)
            assertTrue(EasterEggTimeline.clownTravel(t) in 0f..1f)
            t += 0.05f
        }
    }
}

class ConfettiParticleTest {
    @Test
    fun aParticleStartsAtItsOriginAndOnlyLivesForItsLifetime() {
        val particle = Particle(
            kind = ParticleKind.Confetti, originX = 10f, originY = 20f, vx = 300f, vy = -200f,
            spawnSeconds = 1f, lifeSeconds = 2f, sizeDp = 8f, rotation0 = 0f, spin = 90f, colorIndex = 0, phase = 0f,
        )
        assertEquals(10f, particle.xAt(1f))
        assertEquals(20f, particle.yAt(1f))
        assertFalse(particle.isAliveAt(0.5f))
        assertTrue(particle.isAliveAt(1f))
        assertTrue(particle.isAliveAt(2.9f))
        assertFalse(particle.isAliveAt(3.1f))
    }

    @Test
    fun gravityPullsEveryParticleDownEvenWithoutAnyUpwardSpeed() {
        val particle = Particle(
            kind = ParticleKind.Spark, originX = 0f, originY = 0f, vx = 0f, vy = 0f,
            spawnSeconds = 0f, lifeSeconds = 3f, sizeDp = 6f, rotation0 = 0f, spin = 0f, colorIndex = 1, phase = 0f,
        )
        assertTrue(particle.yAt(0.5f) > 0f)
        assertTrue(particle.yAt(1.5f) > particle.yAt(0.5f))
    }

    @Test
    fun aBurstHasTheRequestedCountAndFliesInEveryDirection() {
        val burst = generateBurst(Random(7), originX = 0f, originY = 0f, count = 120, spawnSeconds = 0.1f, power = 1f)
        assertEquals(120, burst.size)
        assertTrue(burst.any { it.vx > 0f } && burst.any { it.vx < 0f })
        assertTrue(burst.any { it.vy > 0f } && burst.any { it.vy < 0f })
        assertTrue(burst.all { it.lifeSeconds > 0f && it.sizeDp > 0f })
    }

    @Test
    fun aBurstMixesAllTheKindsOfParticles() {
        val burst = generateBurst(Random(3), 0f, 0f, count = 200, spawnSeconds = 0f, power = 1f)
        assertEquals(ParticleKind.entries.filter { !it.isProp }.toSet(), burst.map { it.kind }.toSet())
    }

    @Test
    fun theSameSeedGivesTheSameBurstAndAnotherSeedADifferentOne() {
        val a = generateBurst(Random(1), 0f, 0f, 40, 0f, 1f)
        val b = generateBurst(Random(1), 0f, 0f, 40, 0f, 1f)
        val c = generateBurst(Random(2), 0f, 0f, 40, 0f, 1f)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun everyParticleIsGoneBeforeTheShowEnds() {
        val end = EasterEggTimeline.TotalSeconds
        val particles = easterEggParticles(Random(5))
        assertTrue(particles.isNotEmpty())
        assertTrue(particles.all { it.spawnSeconds + it.lifeSeconds <= end }, "no particle outlives the show")
    }
}

class ConfettiRainTest {
    @Test
    fun theRainStartsAboveTheScreenAndOnlyFallsDownward() {
        val rain = generateRain(Random(9), count = 80, fromSeconds = 0.9f, untilSeconds = 1.9f)
        assertEquals(80, rain.size)
        assertTrue(rain.all { it.anchor == ParticleAnchor.Center })
        assertTrue(rain.all { it.originY < -300f }, "it starts above the top edge")
        assertTrue(rain.all { it.vy > 0f })
        assertTrue(rain.all { it.spawnSeconds in 0.9f..1.9f })
    }

    @Test
    fun theRainIsSpreadAcrossTheWidthAndTheTime() {
        val rain = generateRain(Random(4), count = 200, fromSeconds = 1f, untilSeconds = 2f)
        assertTrue(rain.any { it.originX < -100f } && rain.any { it.originX > 100f })
        assertTrue(rain.any { it.spawnSeconds < 1.3f } && rain.any { it.spawnSeconds > 1.7f })
    }

    @Test
    fun noParticleOutlivesTheShowWhateverTheSeed() {
        val end = EasterEggTimeline.TotalSeconds
        for (seed in 1..40) {
            assertTrue(easterEggParticles(Random(seed)).all { it.spawnSeconds + it.lifeSeconds <= end + 0.0001f }, "seed $seed")
        }
    }
}

class FlyingPropsTest {
    private val props = generateProps(Random(21), count = 24, fromSeconds = 0.5f, untilSeconds = 2.2f)

    @Test
    fun everyPropIsABigStickerThatIsLaunchedUpwardFromBelowTheScreen() {
        assertEquals(24, props.size)
        assertTrue(props.all { it.kind.isProp })
        assertTrue(props.all { it.sizeDp in 56f..140f })
        assertTrue(props.all { it.anchor == ParticleAnchor.Center && it.originY > 300f })
        assertTrue(props.all { it.vy < -700f })
        assertTrue(props.all { it.spawnSeconds in 0.5f..2.2f })
    }

    @Test
    fun theWholeSetOfPropsTakesPart() {
        assertEquals(ParticleKind.entries.filter { it.isProp }.toSet(), props.map { it.kind }.toSet())
    }

    @Test
    fun aBalloonFloatsHigherThanAHeavyPropLaunchedAtTheSameSpeed() {
        fun fly(kind: ParticleKind, gravityScale: Float) = Particle(
            kind = kind, originX = 0f, originY = 0f, vx = 0f, vy = -1000f, spawnSeconds = 0f, lifeSeconds = 3f,
            sizeDp = 90f, rotation0 = 0f, spin = 0f, colorIndex = 0, phase = 0f, gravityScale = gravityScale,
        )
        val balloon = props.first { it.kind == ParticleKind.Balloon }
        val heavy = props.first { it.kind == ParticleKind.Drum }
        assertTrue(balloon.gravityScale < heavy.gravityScale)
        assertTrue(fly(ParticleKind.Balloon, balloon.gravityScale).yAt(1.5f) < fly(ParticleKind.Drum, heavy.gravityScale).yAt(1.5f))
    }

    @Test
    fun noPropOutlivesTheShowAndTheShowCarriesThem() {
        val end = EasterEggTimeline.TotalSeconds
        for (seed in 1..30) {
            val all = easterEggParticles(Random(seed))
            assertTrue(all.any { it.kind.isProp }, "seed $seed")
            assertTrue(all.all { it.spawnSeconds + it.lifeSeconds <= end + 0.0001f }, "seed $seed")
        }
    }
}
