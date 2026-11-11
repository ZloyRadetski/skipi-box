// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ShellAndConfigValueTest {
    @Test
    fun aQuotedValueIsOneShellWordWhateverItHolds() {
        assertEquals("'plain'", "plain".shellQuote())
        assertEquals("'two words'", "two words".shellQuote())
        assertEquals("'it'\\''s'", "it's".shellQuote())
        assertEquals("'\$HOME;rm -rf /'", "\$HOME;rm -rf /".shellQuote())
        assertEquals("''", "".shellQuote())
    }

    @Test
    fun configFlagsAcceptTheUsualSpellings() {
        listOf("true", "TRUE", " yes ", "1").forEach { assertEquals(true, it.toConfigBooleanOrDefault(false), it) }
        listOf("false", "No", "0").forEach { assertEquals(false, it.toConfigBooleanOrDefault(true), it) }
    }

    @Test
    fun anythingElseFallsBackToTheDefault() {
        assertEquals(true, "maybe".toConfigBooleanOrDefault(true))
        assertEquals(false, null.toConfigBooleanOrDefault(false))
        assertEquals(true, "".toConfigBooleanOrDefault(true))
    }
}

class PowerShellLiteralTest {
    @Test
    fun aLiteralIsSingleQuotedWithInnerQuotesDoubled() {
        assertEquals("'C:\\Program Files\\SKIPI'", "C:\\Program Files\\SKIPI".powerShellLiteral())
        assertEquals("'it''s'", "it's".powerShellLiteral())
        assertEquals("''", "".powerShellLiteral())
    }
}
