// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.View.OnFocusChangeListener
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import features.proxy.server.editor.JsonEditorColors
import features.proxy.server.editor.rememberJsonEditorColors
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

@Stable
internal class ConfCodeEditorState(
    initialText: String = "",
) {
    private var editor: CodeEditor? = null
    private var retainedText = initialText
    private var moveCursorToEnd = true

    var documentVersion by mutableIntStateOf(0)
        private set

    var isEmpty by mutableStateOf(initialText.isEmpty())
        private set

    var isFocused by mutableStateOf(false)
        private set

    internal fun attach(editor: CodeEditor) {
        if (this.editor === editor) return
        this.editor = editor
        applyRetainedText(editor)
    }

    internal fun detach(editor: CodeEditor) {
        if (this.editor !== editor) return
        retainedText = editor.text.toString()
        isEmpty = editor.text.length == 0
        isFocused = false
        this.editor = null
    }

    internal fun onContentChanged(editor: CodeEditor, action: Int) {
        if (this.editor !== editor || action == ContentChangeEvent.ACTION_SET_NEW_TEXT) return
        isEmpty = editor.text.length == 0
        documentVersion += 1
    }

    internal fun onFocusChanged(editor: CodeEditor, focused: Boolean) {
        if (this.editor === editor) {
            isFocused = focused
        }
    }

    fun snapshotText(): String = editor?.text?.toString() ?: retainedText

    fun replaceText(text: String, placeCursorAtEnd: Boolean = true) {
        if (text == retainedText && editor?.text?.toString() == text) return
        retainedText = text
        moveCursorToEnd = placeCursorAtEnd
        isEmpty = text.isEmpty()
        editor?.let(::applyRetainedText)
        documentVersion += 1
    }

    private fun applyRetainedText(editor: CodeEditor) {
        editor.setText(retainedText)
        if (moveCursorToEnd) {
            val lastLine = editor.lineCount - 1
            editor.setSelection(lastLine, editor.text.getColumnCount(lastLine), false)
        } else {
            editor.setSelection(0, 0, false)
        }
    }
}

@Composable
internal fun ConfCodeEditor(
    state: ConfCodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val colors = rememberJsonEditorColors()
    val colorScheme = remember(colors) { colors.toConfSoraColorScheme() }
    val shape = RoundedCornerShape(TextFieldDefaults.CornerRadius)
    val borderWidth by animateDpAsState(if (state.isFocused) FocusedBorderWidth else 0.dp)
    val borderColor by animateColorAsState(
        if (state.isFocused) colors.accent else colors.border,
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.background)
            .border(borderWidth, borderColor, shape),
    ) {
        AndroidView(
            factory = { context ->
                ConfSoraEditor(context).also { editor ->
                    editor.configure(colors, colorScheme)
                    editor.bindState(state)
                }
            },
            update = { editor ->
                editor.bindState(state)
                if (editor.colorScheme !== colorScheme) {
                    editor.colorScheme = colorScheme
                }
                editor.isEnabled = true
                editor.isFocusable = true
                editor.isFocusableInTouchMode = true
                editor.isEditable = !readOnly
            },
            onRelease = { editor ->
                editor.bindState(null)
                editor.release()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private class ConfSoraEditor(context: Context) : CodeEditor(context) {
    private var boundState: ConfCodeEditorState? = null

    init {
        subscribeAlways(ContentChangeEvent::class.java) { event ->
            boundState?.onContentChanged(this, event.action)
        }
        onFocusChangeListener = OnFocusChangeListener { _, focused ->
            boundState?.onFocusChanged(this, focused)
        }
    }

    fun bindState(state: ConfCodeEditorState?) {
        if (boundState === state) return
        boundState?.detach(this)
        boundState = state
        state?.attach(this)
    }
}

private fun ConfSoraEditor.configure(
    colors: JsonEditorColors,
    colorScheme: EditorColorScheme,
) {
    setTextSize(EditorTextSize)
    setTypefaceText(Typeface.MONOSPACE)
    setTypefaceLineNumber(Typeface.MONOSPACE)
    setLineSpacing(dp(EditorLineSpacing).toFloat(), 1f)
    setTabWidth(EditorTabWidth)
    setWordwrap(false)
    setPinLineNumber(true)
    setLineNumberEnabled(true)
    setDisplayLnPanel(false)
    setLineNumberMarginLeft(dp(LineNumberMargin).toFloat())
    setDividerWidth(dp(DividerWidth).toFloat())
    setHighlightCurrentBlock(false)
    setBlockLineEnabled(false)
    setRenderFunctionCharacters(false)
    setLigatureEnabled(false)
    setScalable(true)
    setCursorAnimationEnabled(true)
    setInputType(
        InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
    )
    setPadding(0, dp(EditorVerticalPadding), dp(EditorHorizontalPadding), dp(EditorVerticalPadding))
    setEdgeEffectColor(colors.accent.toArgb())
    this.colorScheme = colorScheme
    setEditorLanguage(ConfSoraLanguage())
}

private fun CodeEditor.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

private class ConfSoraLanguage : EmptyLanguage() {
    private val analyzer = ConfIncrementalAnalyzeManager()

    override fun getAnalyzeManager(): AnalyzeManager = analyzer
}

private class ConfIncrementalAnalyzeManager : AsyncIncrementalAnalyzeManager<Unit, ConfToken>(true) {
    override fun getInitialState() = Unit

    override fun stateEquals(state: Unit, another: Unit): Boolean = true

    override fun tokenizeLine(
        line: CharSequence,
        state: Unit,
        lineIndex: Int,
    ): LineTokenizeResult<Unit, ConfToken> {
        return LineTokenizeResult(Unit, tokenizeConfLine(line))
    }

    override fun generateSpansForLine(tokens: LineTokenizeResult<Unit, ConfToken>): List<Span> {
        val lineTokens = tokens.tokens.orEmpty()
        if (lineTokens.isEmpty()) {
            return listOf(SpanFactory.obtainNoExt(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
        }
        return lineTokens.map { token ->
            SpanFactory.obtainNoExt(token.start, TextStyle.makeStyle(token.kind.soraColorId()))
        }
    }

    override fun computeBlocks(
        text: Content,
        delegate: CodeBlockAnalyzeDelegate,
    ): List<CodeBlock> = emptyList()
}

internal enum class ConfTokenKind {
    Normal,
    Section,
    Key,
    Comment,
    String,
    Number,
    Literal,
    Punctuation,
}

internal data class ConfToken(
    val start: Int,
    val kind: ConfTokenKind,
)

internal fun tokenizeConfLine(line: CharSequence): List<ConfToken> {
    val text = line.toString()
    val trimmed = text.trimStart()
    if (trimmed.isEmpty()) {
        return listOf(ConfToken(0, ConfTokenKind.Normal))
    }
    val leadingWhitespace = text.length - trimmed.length
    if (trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("//")) {
        return listOf(ConfToken(leadingWhitespace, ConfTokenKind.Comment))
    }
    if (trimmed.startsWith("[") && trimmed.contains("]")) {
        return listOf(ConfToken(leadingWhitespace, ConfTokenKind.Section))
    }

    val tokens = mutableListOf<ConfToken>()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '#' || char == ';') {
            tokens += ConfToken(index, ConfTokenKind.Comment)
            break
        }
        if (char == '=' || char == ',') {
            tokens += ConfToken(index, ConfTokenKind.Punctuation)
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
            while (index < text.length && text[index] != quote) {
                if (text[index] == '\\' && index + 1 < text.length) index += 1
                index += 1
            }
            if (index < text.length) index += 1
            tokens += ConfToken(start, ConfTokenKind.String)
            continue
        }
        if (char.isDigit()) {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.')) {
                index += 1
            }
            tokens += ConfToken(start, ConfTokenKind.Number)
            continue
        }
        val start = index
        while (index < text.length && !text[index].isWhitespace() && text[index] != '=' && text[index] != ',' && text[index] != '#' && text[index] != ';' && text[index] != '"' && text[index] != '\'') {
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
            tokens.isEmpty() || (tokens.lastOrNull()?.kind == ConfTokenKind.Punctuation && text.substring(0, start).trimEnd().endsWith("=")) -> ConfTokenKind.Key
            else -> ConfTokenKind.Normal
        }
        tokens += ConfToken(start, kind)
    }
    return tokens.ifEmpty { listOf(ConfToken(0, ConfTokenKind.Normal)) }
}

private fun ConfTokenKind.soraColorId(): Int = when (this) {
    ConfTokenKind.Normal -> EditorColorScheme.TEXT_NORMAL
    ConfTokenKind.Section -> EditorColorScheme.ATTRIBUTE_NAME
    ConfTokenKind.Key -> EditorColorScheme.ATTRIBUTE_NAME
    ConfTokenKind.Comment -> SyntaxCommentColorId
    ConfTokenKind.String -> EditorColorScheme.LITERAL
    ConfTokenKind.Number -> SyntaxNumberColorId
    ConfTokenKind.Literal -> SyntaxLiteralColorId
    ConfTokenKind.Punctuation -> EditorColorScheme.OPERATOR
}

private fun JsonEditorColors.toConfSoraColorScheme(): EditorColorScheme {
    return object : EditorColorScheme(darkTheme) {}.apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, background.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, gutter.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, lineNumber.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, accent.toArgb())
        setColor(EditorColorScheme.LINE_DIVIDER, separator.toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, foreground.toArgb())
        setColor(EditorColorScheme.SELECTION_INSERT, accent.toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, accent.toArgb())
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection.toArgb())
        setColor(EditorColorScheme.CURRENT_LINE, currentLine.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, border.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, lineNumber.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, accent.toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, syntax.key.toArgb())
        setColor(EditorColorScheme.LITERAL, syntax.string.toArgb())
        setColor(SyntaxNumberColorId, syntax.number.toArgb())
        setColor(SyntaxLiteralColorId, syntax.literal.toArgb())
        setColor(SyntaxCommentColorId, placeholder.toArgb())
        setColor(EditorColorScheme.OPERATOR, syntax.punctuation.toArgb())
        setColor(EditorColorScheme.IDENTIFIER_NAME, foreground.toArgb())
    }
}

private const val SyntaxNumberColorId = 256
private const val SyntaxLiteralColorId = 257
private const val SyntaxCommentColorId = 258
private const val EditorTextSize = 14f
private const val EditorLineSpacing = 4f
private const val EditorVerticalPadding = 8f
private const val EditorHorizontalPadding = 10f
private const val EditorTabWidth = 2
private const val LineNumberMargin = 4f
private const val DividerWidth = 1f
private val FocusedBorderWidth = 2.dp
