// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfSyntaxTest {
    @Test
    fun sectionHeaderIsOneSectionToken() {
        val tokens = tokenizeConfLine("[General]")
        assertEquals(1, tokens.size)
        assertEquals(ConfTokenKind.Section, tokens[0].kind)
    }

    @Test
    fun commentsStartWithHashSemicolonOrSlashes() {
        for (line in listOf("# This is a comment", "; Another comment", "  // third style")) {
            val tokens = tokenizeConfLine(line)
            assertEquals(1, tokens.size)
            assertEquals(ConfTokenKind.Comment, tokens[0].kind)
        }
    }

    @Test
    fun keyValuePairHasKeyAndPunctuation() {
        val tokens = tokenizeConfLine("loglevel = notify")
        assertTrue(tokens.any { it.kind == ConfTokenKind.Key })
        assertTrue(tokens.any { it.kind == ConfTokenKind.Punctuation })
    }

    @Test
    fun ruleLineSplitsIntoKeyPunctuationAndPolicyLiteral() {
        val tokens = tokenizeConfLine("DOMAIN-SUFFIX,google.com,PROXY")
        assertEquals(
            listOf(
                ConfTokenKind.Key, ConfTokenKind.Punctuation, ConfTokenKind.Normal,
                ConfTokenKind.Punctuation, ConfTokenKind.Literal,
            ),
            tokens.map { it.kind },
        )
    }

    @Test
    fun emptyLineIsOneNormalToken() {
        assertEquals(listOf(ConfToken(0, ConfTokenKind.Normal)), tokenizeConfLine("   "))
    }

    @Test
    fun highlightingUsesTheCommentColorForCommentsAndKeepsText() {
        val syntax = JsonSyntaxColors(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Gray)
        val comment = Color.Magenta
        val text = "[Rule]\nDOMAIN,a.example,DIRECT # note"

        val highlighted = highlightConf(text, syntax, normal = Color.White, comment = comment)

        assertEquals(text, highlighted.text)
        fun colorAt(index: Int) = highlighted.spanStyles.lastOrNull { index >= it.start && index < it.end }?.item?.color
        assertEquals(Color.Red, colorAt(1)) // section
        assertEquals(Color.Yellow, colorAt(text.indexOf("DIRECT")))
        assertEquals(comment, colorAt(text.indexOf("note")))
    }
}
