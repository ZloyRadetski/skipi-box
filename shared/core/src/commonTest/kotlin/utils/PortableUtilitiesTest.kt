// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PortableUtilitiesTest {
    @Test
    fun flexibleBase64SupportsWhitespaceAndUrlSafeValues() {
        assertEquals("SKIPI", "U0tJUEk=".decodeFlexibleBase64OrNull()?.decodeToString())
        assertEquals("?", "Pw".decodeFlexibleBase64OrNull()?.decodeToString())
        assertNull("not-base64!".decodeFlexibleBase64OrNull())
    }

    @Test
    fun urlDecodingKeepsLiteralPlus() {
        assertEquals("A+B C", "A%2BB%20C".decodeUrlComponentPreservingPlus())
    }

    @Test
    fun valueParsingRemovesBlankValuesAndKeepsDistinctOrder() {
        assertEquals(listOf("one", "two"), listOf(" one ", "", "two", "one").toTrimmedNonEmptyDistinctList())
        assertEquals(listOf("one", "two"), " one, ,two,one ".toDistinctCsvValues())
        assertEquals(4, " 4 ".toIntInRangeOrNull(1..5))
        assertNull("8".toIntInRangeOrNull(1..5))
    }
}
