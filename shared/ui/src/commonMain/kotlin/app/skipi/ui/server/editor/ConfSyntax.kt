// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

enum class ConfTokenKind {
    Normal,
    Section,
    Key,
    Comment,
    String,
    Number,
    Literal,
    Punctuation,
}

data class ConfToken(
    val start: Int,
    val kind: ConfTokenKind,
)

fun tokenizeConfLine(line: CharSequence): List<ConfToken> {
    val text = line.toString()
    return tokenizeConfRange(text, 0, text.length)
}

/** Tokenizes `text[from, to)` without copying it; token starts are relative to `from`. */
internal fun tokenizeConfRange(text: String, from: Int, to: Int): List<ConfToken> {
    var first = from
    while (first < to && text[first].isWhitespace()) first += 1
    if (first == to) {
        return listOf(ConfToken(0, ConfTokenKind.Normal))
    }
    val leadingWhitespace = first - from
    if (text.startsWith("#", first) || text.startsWith(";", first) || text.startsWith("//", first)) {
        return listOf(ConfToken(leadingWhitespace, ConfTokenKind.Comment))
    }
    if (text[first] == '[' && text.hasCharIn(']', first, to)) {
        return listOf(ConfToken(leadingWhitespace, ConfTokenKind.Section))
    }

    val tokens = mutableListOf<ConfToken>()
    var index = from
    while (index < to) {
        val char = text[index]
        if (char == '#' || char == ';') {
            tokens += ConfToken(index - from, ConfTokenKind.Comment)
            break
        }
        if (char == '=' || char == ',') {
            tokens += ConfToken(index - from, ConfTokenKind.Punctuation)
            index += 1
            continue
        }
        if (char.isWhitespace()) {
            index += 1
            continue
        }
        if (char == '"' || char == '\'') {
            val quote = char
            val start = index
            index += 1
            while (index < to && text[index] != quote) {
                if (text[index] == '\\' && index + 1 < to) index += 1
                index += 1
            }
            if (index < to) index += 1
            tokens += ConfToken(start - from, ConfTokenKind.String)
            continue
        }
        if (char.isDigit()) {
            val start = index
            while (index < to && (text[index].isDigit() || text[index] == '.')) {
                index += 1
            }
            tokens += ConfToken(start - from, ConfTokenKind.Number)
            continue
        }
        val start = index
        while (index < to && !text[index].isWhitespace() && text[index] != '=' && text[index] != ',' && text[index] != '#' && text[index] != ';' && text[index] != '"' && text[index] != '\'') {
            index += 1
        }
        val word = text.substring(start, index)
        val kind = when {
            word.equals("true", ignoreCase = true) ||
                word.equals("false", ignoreCase = true) ||
                word.equals("null", ignoreCase = true) ||
                word.equals("PROXY", ignoreCase = true) ||
                word.equals("DIRECT", ignoreCase = true) ||
                word.equals("REJECT", ignoreCase = true) ||
                word.equals("select", ignoreCase = true) ||
                word.equals("url-test", ignoreCase = true) ||
                word.equals("fallback", ignoreCase = true) -> ConfTokenKind.Literal

            word.contains(':') -> ConfTokenKind.String
            tokens.isEmpty() || (tokens.last().kind == ConfTokenKind.Punctuation && text.lastNonBlankBefore(start, from) == '=') -> ConfTokenKind.Key
            else -> ConfTokenKind.Normal
        }
        tokens += ConfToken(start - from, kind)
    }
    return tokens.ifEmpty { listOf(ConfToken(0, ConfTokenKind.Normal)) }
}

/** Colors a Shadowrocket-style profile line by line; the text itself is returned unchanged. */
fun highlightConf(text: String, colors: JsonSyntaxColors, normal: Color, comment: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        var lineStart = 0
        while (lineStart <= text.length) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) text.length else newline
            val lineLength = lineEnd - lineStart
            val tokens = tokenizeConfRange(text, lineStart, lineEnd)
            tokens.forEachIndexed { index, token ->
                val tokenEnd = tokens.getOrNull(index + 1)?.start ?: lineLength
                if (tokenEnd > token.start) {
                    addStyle(
                        SpanStyle(color = token.kind.color(colors, normal, comment)),
                        lineStart + token.start,
                        lineStart + tokenEnd,
                    )
                }
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
    }

private fun ConfTokenKind.color(colors: JsonSyntaxColors, normal: Color, comment: Color): Color = when (this) {
    ConfTokenKind.Normal -> normal
    ConfTokenKind.Section, ConfTokenKind.Key -> colors.key
    ConfTokenKind.Comment -> comment
    ConfTokenKind.String -> colors.string
    ConfTokenKind.Number -> colors.number
    ConfTokenKind.Literal -> colors.literal
    ConfTokenKind.Punctuation -> colors.punctuation
}

/** Last non-whitespace char in `[from, end)`, scanning backwards; null if there is none. */
private fun String.lastNonBlankBefore(end: Int, from: Int): Char? {
    var i = end - 1
    while (i >= from) {
        if (!this[i].isWhitespace()) return this[i]
        i -= 1
    }
    return null
}

private fun String.hasCharIn(char: Char, from: Int, to: Int): Boolean {
    for (i in from until to) if (this[i] == char) return true
    return false
}
