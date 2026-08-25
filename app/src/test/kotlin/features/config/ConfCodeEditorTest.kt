// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfCodeEditorTest {

    @Test
    fun testTokenizeSectionHeader() {
        val tokens = tokenizeConfLine("[General]")
        assertEquals(1, tokens.size)
        assertEquals(ConfTokenKind.Section, tokens[0].kind)
    }

    @Test
    fun testTokenizeComment() {
        val tokensHash = tokenizeConfLine("# This is a comment")
        assertEquals(1, tokensHash.size)
        assertEquals(ConfTokenKind.Comment, tokensHash[0].kind)

        val tokensSemicolon = tokenizeConfLine("; Another comment")
        assertEquals(1, tokensSemicolon.size)
        assertEquals(ConfTokenKind.Comment, tokensSemicolon[0].kind)
    }

    @Test
    fun testTokenizeKeyValuePair() {
        val tokens = tokenizeConfLine("loglevel = notify")
        assertTrue(tokens.any { it.kind == ConfTokenKind.Key })
        assertTrue(tokens.any { it.kind == ConfTokenKind.Punctuation })
    }

    @Test
    fun testTokenizeRuleLine() {
        val tokens = tokenizeConfLine("DOMAIN-SUFFIX,google.com,PROXY")
        assertEquals(5, tokens.size)
        assertEquals(ConfTokenKind.Key, tokens[0].kind) // DOMAIN-SUFFIX
        assertEquals(ConfTokenKind.Punctuation, tokens[1].kind) // ,
        assertEquals(ConfTokenKind.Normal, tokens[2].kind) // google.com
        assertEquals(ConfTokenKind.Punctuation, tokens[3].kind) // ,
        assertEquals(ConfTokenKind.Literal, tokens[4].kind) // PROXY
    }

    @Test
    fun testConfCodeEditorState() {
        val state = ConfCodeEditorState("initial raw config")
        assertEquals("initial raw config", state.snapshotText())
        state.replaceText("updated raw config")
        assertEquals("updated raw config", state.snapshotText())
    }
}
