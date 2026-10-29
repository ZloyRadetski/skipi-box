// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Raw JSON editor with syntax colors and a line-number gutter. */
@Composable
fun SkipiJsonCodeEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    scrollsInternally: Boolean = true,
) = SkipiCodeEditor(
    label = label,
    value = value,
    onValueChange = onValueChange,
    highlight = { text, colors -> highlightJson(text, colors.syntax, colors.foreground) },
    modifier = modifier,
    readOnly = readOnly,
    scrollsInternally = scrollsInternally,
)

/** Shadowrocket-style profile editor with syntax colors and a line-number gutter. */
@Composable
fun SkipiConfCodeEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    scrollsInternally: Boolean = true,
) = SkipiCodeEditor(
    label = label,
    value = value,
    onValueChange = onValueChange,
    highlight = { text, colors -> highlightConf(text, colors.syntax, colors.foreground, comment = colors.placeholder) },
    modifier = modifier,
    readOnly = readOnly,
    scrollsInternally = scrollsInternally,
)

/**
 * Code editor for hosts without a native one. The text is a plain string owned by the caller;
 * the cursor is kept internally and highlighting is re-applied through a visual transformation.
 */
@Composable
fun SkipiCodeEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    highlight: (text: String, colors: JsonEditorColors) -> AnnotatedString,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    scrollsInternally: Boolean = true,
) {
    val colors = rememberJsonEditorColors()
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // An external replacement (for example "Format JSON") moves the cursor to the end.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TextFieldDefaults.CornerRadius)
    val borderWidth by animateDpAsState(if (focused) 2.dp else 0.dp)
    val borderColor by animateColorAsState(if (focused) colors.accent else colors.border)
    val textStyle = TextStyle(
        color = colors.foreground,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val transformation = remember(colors, highlight) {
        VisualTransformation { text ->
            TransformedText(highlight(text.text, colors), OffsetMapping.Identity)
        }
    }
    val lineCount = field.text.count { it == '\n' } + 1
    val gutterText = remember(lineCount) { (1..lineCount).joinToString("\n") }

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.background)
            .border(borderWidth, borderColor, shape),
    ) {
        Row(
            modifier = if (scrollsInternally) {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            } else {
                // Grows with the document so a surrounding page scrolls instead.
                Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(colors.gutter)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                BasicText(
                    text = gutterText,
                    style = textStyle.copy(color = colors.lineNumber, textAlign = TextAlign.End),
                    modifier = Modifier.widthIn(min = 32.dp),
                )
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(colors.separator))
            BasicTextField(
                value = field,
                onValueChange = { updated ->
                    field = updated
                    if (updated.text != value) onValueChange(updated.text)
                },
                readOnly = readOnly,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = transformation,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (field.text.isEmpty()) {
            BasicText(
                text = label,
                style = MiuixTheme.textStyles.body2.copy(color = colors.placeholder),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 50.dp, top = 9.dp),
            )
        }
    }
}
