// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Colors of the raw-JSON editor, derived from the theme's primary color so every host matches. */
data class JsonEditorColors(
    val darkTheme: Boolean,
    val accent: Color,
    val foreground: Color,
    val background: Color,
    val gutter: Color,
    val separator: Color,
    val border: Color,
    val lineNumber: Color,
    val placeholder: Color,
    val selection: Color,
    val currentLine: Color,
    val formatButtonBackground: Color,
    val syntax: JsonSyntaxColors,
)

data class JsonSyntaxColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
    val punctuation: Color,
)

@Composable
fun rememberJsonEditorColors(): JsonEditorColors {
    val colorScheme = MiuixTheme.colorScheme
    val primary = colorScheme.primary
    val background = colorScheme.secondaryContainer
    val foreground = colorScheme.onSurface
    val darkTheme = SkipiTheme.colors.isDark
    val onSurfaceVariantSummary = colorScheme.onSurfaceVariantSummary
    val onSecondaryContainer = colorScheme.onSecondaryContainer
    return remember(primary, background, foreground, darkTheme, onSurfaceVariantSummary, onSecondaryContainer) {
        val primaryHue = primary.hue()
        JsonEditorColors(
            darkTheme = darkTheme,
            accent = primary,
            foreground = foreground,
            background = background,
            gutter = enhancedThemeColor(primaryHue, darkTheme),
            separator = primary.copy(alpha = if (darkTheme) 0.24f else 0.18f),
            border = primary.copy(alpha = if (darkTheme) 0.20f else 0.14f),
            lineNumber = onSurfaceVariantSummary.copy(alpha = if (darkTheme) 0.78f else 0.68f),
            placeholder = onSecondaryContainer.copy(alpha = if (darkTheme) 0.70f else 0.58f),
            selection = primary.copy(alpha = if (darkTheme) 0.34f else 0.24f),
            currentLine = primary.copy(alpha = if (darkTheme) 0.10f else 0.06f),
            formatButtonBackground = primary.copy(alpha = if (darkTheme) 0.18f else 0.14f),
            syntax = JsonSyntaxColors(
                key = vividThemeColor(primaryHue, hueOffset = 0f, darkTheme = darkTheme),
                string = vividThemeColor(primaryHue, hueOffset = 88f, darkTheme = darkTheme),
                number = vividThemeColor(primaryHue, hueOffset = -52f, darkTheme = darkTheme),
                literal = vividThemeColor(primaryHue, hueOffset = 176f, darkTheme = darkTheme),
                punctuation = onSurfaceVariantSummary,
            ),
        )
    }
}

enum class JsonTokenKind {
    Normal,
    Key,
    String,
    Number,
    Literal,
    Punctuation,
}

data class JsonToken(
    val start: Int,
    val kind: JsonTokenKind,
)

/** Splits one line into colored runs; each token spans from its start to the next token's start. */
fun tokenizeJsonLine(line: CharSequence): List<JsonToken> {
    val text = line.toString()
    val tokens = mutableListOf<JsonToken>()
    var index = 0
    while (index < text.length) {
        val start = index
        when (text[index]) {
            '"' -> {
                index = text.stringTokenEnd(index)
                tokens += JsonToken(
                    start = start,
                    kind = if (text.isObjectKey(index)) JsonTokenKind.Key else JsonTokenKind.String,
                )
            }

            '-', in '0'..'9' -> {
                index = text.numberTokenEnd(index)
                tokens += JsonToken(start, JsonTokenKind.Number)
            }

            't', 'f', 'n' -> {
                val end = text.literalTokenEnd(index)
                if (end > index) {
                    index = end
                    tokens += JsonToken(start, JsonTokenKind.Literal)
                } else {
                    index += 1
                    tokens += JsonToken(start, JsonTokenKind.Normal)
                }
            }

            '{', '}', '[', ']', ':', ',' -> {
                index += 1
                tokens += JsonToken(start, JsonTokenKind.Punctuation)
            }

            else -> {
                index += 1
                while (index < text.length && !text.isJsonTokenStart(index)) {
                    index += 1
                }
                tokens += JsonToken(start, JsonTokenKind.Normal)
            }
        }
    }
    return tokens.ifEmpty { listOf(JsonToken(0, JsonTokenKind.Normal)) }
}

/** Colors a whole document line by line; the text itself is returned unchanged. */
fun highlightJson(text: String, colors: JsonSyntaxColors, normal: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    var lineStart = 0
    while (lineStart <= text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        val line = text.substring(lineStart, lineEnd)
        val tokens = tokenizeJsonLine(line)
        tokens.forEachIndexed { index, token ->
            val tokenEnd = tokens.getOrNull(index + 1)?.start ?: line.length
            if (tokenEnd > token.start) {
                addStyle(
                    SpanStyle(color = token.kind.color(colors, normal)),
                    lineStart + token.start,
                    lineStart + tokenEnd,
                )
            }
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
}

private fun JsonTokenKind.color(colors: JsonSyntaxColors, normal: Color): Color = when (this) {
    JsonTokenKind.Normal -> normal
    JsonTokenKind.Key -> colors.key
    JsonTokenKind.String -> colors.string
    JsonTokenKind.Number -> colors.number
    JsonTokenKind.Literal -> colors.literal
    JsonTokenKind.Punctuation -> colors.punctuation
}

private fun String.isJsonTokenStart(index: Int): Boolean {
    val char = this[index]
    return char == '"' ||
        char == '-' ||
        char.isDigit() ||
        char in JsonLiteralStarts ||
        char in JsonPunctuation
}

private fun String.stringTokenEnd(start: Int): Int {
    var index = start + 1
    var escaped = false
    while (index < length) {
        val char = this[index]
        if (escaped) {
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == '"') {
            return index + 1
        }
        index += 1
    }
    return length
}

private fun String.isObjectKey(stringEnd: Int): Boolean {
    var index = stringEnd
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return index < length && this[index] == ':'
}

private fun String.numberTokenEnd(start: Int): Int {
    var index = start
    while (index < length && this[index] in JsonNumberTokenChars) {
        index += 1
    }
    return index
}

private fun String.literalTokenEnd(start: Int): Int {
    val literal = JsonLiterals.firstOrNull { literal ->
        startsWith(literal, startIndex = start)
    } ?: return start
    val end = start + literal.length
    val boundaryBefore = start == 0 || !this[start - 1].isLetter()
    val boundaryAfter = end == length || !this[end].isLetter()
    return if (boundaryBefore && boundaryAfter) end else start
}

/** Hue in degrees of the HSV wheel; grays report 0. */
fun Color.hue(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return 0f
    val hue = when (max) {
        red -> ((green - blue) / delta) % 6f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return if (hue < 0f) hue + 360f else hue
}

fun vividThemeColor(
    baseHue: Float,
    hueOffset: Float,
    darkTheme: Boolean,
): Color {
    val hue = (baseHue + hueOffset).floorMod(360f)
    val saturation = if (darkTheme) 0.78f else 0.86f
    val value = if (darkTheme) 0.96f else 0.70f
    return Color.hsv(hue = hue, saturation = saturation, value = value)
}

fun enhancedThemeColor(
    baseHue: Float,
    darkTheme: Boolean,
): Color {
    val saturation = if (darkTheme) 0.24f else 0.20f
    val value = if (darkTheme) 0.26f else 0.96f
    return Color.hsv(hue = (baseHue + 10f).floorMod(360f), saturation = saturation, value = value)
}

private fun Float.floorMod(modulus: Float): Float {
    val result = this % modulus
    return if (result < 0f) result + modulus else result
}

private val JsonNumberTokenChars = setOf('-', '+', '.', 'e', 'E') + ('0'..'9')
private val JsonLiterals = listOf("true", "false", "null")
private val JsonLiteralStarts = setOf('t', 'f', 'n')
private val JsonPunctuation = setOf('{', '}', '[', ']', ':', ',')
