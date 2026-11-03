// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCommandTokenTest {

    @Test
    fun aCommandCarryingTheInstallTokenIsAccepted() {
        assertTrue(WidgetCommandToken.matches(provided = "abc123", expected = "abc123"))
    }

    @Test
    fun commandsFromOtherAppsAreRejected() {
        assertFalse(WidgetCommandToken.matches(provided = null, expected = "abc123"))
        assertFalse(WidgetCommandToken.matches(provided = "", expected = "abc123"))
        assertFalse(WidgetCommandToken.matches(provided = "abc124", expected = "abc123"))
        assertFalse(WidgetCommandToken.matches(provided = "abc12", expected = "abc123"))
    }

    @Test
    fun anEmptyExpectedTokenNeverMatches() {
        assertFalse(WidgetCommandToken.matches(provided = "", expected = ""))
    }

    @Test
    fun generatedTokensAreLongHexAndUnique() {
        val first = WidgetCommandToken.generate()
        val second = WidgetCommandToken.generate()

        assertEquals(32, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first, second)
    }
}
