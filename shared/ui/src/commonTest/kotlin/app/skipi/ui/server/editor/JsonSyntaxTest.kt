// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonSyntaxTest {
    private fun kinds(line: String) = tokenizeJsonLine(line).map { it.kind }

    @Test
    fun keysAndStringsAreDistinguishedByTheFollowingColon() {
        val tokens = tokenizeJsonLine("""  "tag" : "proxy",""")

        assertEquals(
            listOf(JsonTokenKind.Normal, JsonTokenKind.Key, JsonTokenKind.Normal, JsonTokenKind.Punctuation, JsonTokenKind.Normal, JsonTokenKind.String, JsonTokenKind.Punctuation),
            tokens.map { it.kind },
        )
        assertEquals(listOf(0, 2, 7, 8, 9, 10, 17), tokens.map { it.start })
    }

    @Test
    fun numbersLiteralsAndPunctuationHaveTheirOwnKinds() {
        assertEquals(listOf(JsonTokenKind.Number), kinds("-12.5e+3"))
        assertEquals(listOf(JsonTokenKind.Literal, JsonTokenKind.Punctuation, JsonTokenKind.Literal), kinds("true,null"))
        assertEquals(listOf(JsonTokenKind.Punctuation, JsonTokenKind.Punctuation), kinds("{}"))
    }

    @Test
    fun wordsThatMerelyStartLikeLiteralsStayNormal() {
        assertTrue(kinds("nullable").all { it == JsonTokenKind.Normal })
        assertTrue(kinds("falsey").all { it == JsonTokenKind.Normal })
    }

    @Test
    fun escapedQuotesDoNotEndAStringAndUnterminatedStringsRunToTheEnd() {
        val escaped = tokenizeJsonLine("""["a\"b", 1]""")
        val unterminated = tokenizeJsonLine(""""open string""")

        assertEquals(JsonTokenKind.String, escaped[1].kind)
        assertEquals(JsonTokenKind.Punctuation, escaped[2].kind)
        assertEquals(listOf(JsonTokenKind.String), unterminated.map { it.kind })
    }

    @Test
    fun anEmptyLineIsOneNormalToken() {
        assertEquals(listOf(JsonToken(0, JsonTokenKind.Normal)), tokenizeJsonLine(""))
    }

    @Test
    fun highlightingColorsEveryTokenAndKeepsTheText() {
        val colors = JsonSyntaxColors(
            key = Color.Red, string = Color.Green, number = Color.Blue, literal = Color.Yellow, punctuation = Color.Gray,
        )
        val text = "{\n  \"a\": [1, true, \"x\"]\n}"

        val highlighted = highlightJson(text, colors, normal = Color.White)

        assertEquals(text, highlighted.text)
        fun colorAt(index: Int) = highlighted.spanStyles.lastOrNull { index >= it.start && index < it.end }?.item?.color
        assertEquals(Color.Gray, colorAt(text.indexOf('{')))
        assertEquals(Color.Red, colorAt(text.indexOf("\"a\"") + 1))
        assertEquals(Color.Blue, colorAt(text.indexOf('1')))
        assertEquals(Color.Yellow, colorAt(text.indexOf("true")))
        assertEquals(Color.Green, colorAt(text.indexOf("\"x\"") + 1))
    }

    @Test
    fun hueOfPrimaryColorsMatchesTheHsvWheel() {
        assertEquals(0f, Color(0xFFFF0000).hue(), 0.5f)
        assertEquals(120f, Color(0xFF00FF00).hue(), 0.5f)
        assertEquals(240f, Color(0xFF0000FF).hue(), 0.5f)
        assertEquals(0f, Color(0xFF808080).hue(), 0.5f)
    }

    @Test
    fun themeColorsRotateTheHueAndStayInGamut() {
        val vivid = vividThemeColor(baseHue = 350f, hueOffset = 20f, darkTheme = true)
        val gutter = enhancedThemeColor(baseHue = 350f, darkTheme = false)

        assertEquals(10f, vivid.hue(), 1f)
        assertTrue(gutter.alpha == 1f)
    }
}
