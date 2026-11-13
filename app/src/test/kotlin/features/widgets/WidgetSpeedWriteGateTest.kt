// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSpeedWriteGateTest {
    @Test
    fun firstWriteAlwaysAllowed_thenAtMostEveryFiveSeconds() {
        val gate = WidgetSpeedWriteGate(minIntervalMillis = 5_000)
        assertTrue(gate.shouldWrite(1_000))
        assertFalse(gate.shouldWrite(2_000))
        assertFalse(gate.shouldWrite(5_999))
        assertTrue(gate.shouldWrite(6_000))
        assertFalse(gate.shouldWrite(7_000))
    }

    @Test
    fun resetAllowsImmediateWrite() {
        val gate = WidgetSpeedWriteGate(minIntervalMillis = 5_000)
        assertTrue(gate.shouldWrite(1_000))
        gate.reset()
        assertTrue(gate.shouldWrite(1_100))
    }

    @Test
    fun defaultInterval_staysBelowTheWidgetFreshnessWindow() {
        assertTrue(WidgetSpeedWriteGate.DefaultIntervalMillis < 15_000L)
    }
}
