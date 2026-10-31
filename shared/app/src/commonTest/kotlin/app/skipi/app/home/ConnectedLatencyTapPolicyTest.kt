// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The compact ping button measures the live connection first and the whole group on a quick repeat. */
class ConnectedLatencyTapPolicyTest {
    private val time = TestTimeSource()
    private val policy = ConnectedLatencyTapPolicy(time)

    @Test
    fun theFirstTapMeasuresTheCurrentConnection() {
        assertEquals(ConnectedLatencyStep.TestConnection, policy.nextStep())
    }

    @Test
    fun aRepeatWithinFiveSecondsMeasuresTheWholeGroup() {
        policy.nextStep()
        time += 4.seconds

        assertEquals(ConnectedLatencyStep.PingGroup, policy.nextStep())
    }

    @Test
    fun aRepeatAfterTheWindowMeasuresTheConnectionAgain() {
        policy.nextStep()
        time += 6.seconds

        assertEquals(ConnectedLatencyStep.TestConnection, policy.nextStep())
    }

    @Test
    fun theWindowRestartsFromTheLatestConnectionMeasurement() {
        policy.nextStep()
        time += 6.seconds
        policy.nextStep()
        time += 4.seconds

        assertEquals(ConnectedLatencyStep.PingGroup, policy.nextStep())
    }

    @Test
    fun afterTheGroupWasMeasuredTheNextTapStartsOverWithTheConnection() {
        policy.nextStep()
        time += 1.seconds
        policy.nextStep()
        time += 1.seconds

        assertEquals(ConnectedLatencyStep.TestConnection, policy.nextStep())
    }
}
