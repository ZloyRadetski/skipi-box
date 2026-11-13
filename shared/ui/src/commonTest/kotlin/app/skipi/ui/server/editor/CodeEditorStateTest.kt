// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CodeEditorStateTest {
    @Test
    fun highlightCacheReusesResultForIdenticalText() {
        var calls = 0
        val cache = HighlightCache { calls += 1; AnnotatedString(it) }
        val first = cache.get("{}")
        assertSame(first, cache.get("{}"))
        assertEquals(1, calls)
        cache.get("[]")
        assertEquals(2, calls)
    }

    @Test
    fun staleEchoKeepsCursorClamped() {
        val field = TextFieldValue("hello world", TextRange(3, 5))
        val result = reconcileExternalValue(field, "hello", lastEmitted = "hello")
        assertEquals(TextRange(3, 5), result.selection)
        assertEquals(TextRange(5, 5), reconcileExternalValue(TextFieldValue("hello world", TextRange(8)), "hello", "hello").selection)
    }

    @Test
    fun fullExternalReplacementMovesCursorToEnd() {
        val field = TextFieldValue("a", TextRange(1))
        assertEquals(TextRange(7), reconcileExternalValue(field, "{\n  }\n ", lastEmitted = "a").selection)
        assertEquals(TextRange(2), reconcileExternalValue(field, "ab", lastEmitted = null).selection)
    }

    @Test
    fun highlightingByIndicesMatchesLineSlices() {
        val colors = JsonSyntaxColors(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Gray)
        val text = "{\n  \"a\": [true, 12, null],\n  \"b\": \"x\"\n}\nfalsey"
        val h = highlightJson(text, colors, Color.White)
        assertEquals(text, h.text)
        fun colorAt(i: Int) = h.spanStyles.lastOrNull { i >= it.start && i < it.end }?.item?.color
        assertEquals(Color.Red, colorAt(text.indexOf("\"a\"")))
        assertEquals(Color.Yellow, colorAt(text.indexOf("true")))
        assertEquals(Color.Blue, colorAt(text.indexOf("12")))
        assertEquals(Color.Green, colorAt(text.indexOf("\"x\"")))
        assertEquals(Color.White, colorAt(text.indexOf("falsey")))
    }
}
