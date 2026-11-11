// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.skipi.ui.server.editor.JsonTokenKind
import app.skipi.ui.server.editor.tokenizeJsonLine
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import ui.editor.CodeEditorState
import ui.editor.SoraCodeEditor
import ui.editor.SyntaxLiteralColorId
import ui.editor.SyntaxNumberColorId
import ui.editor.soraTokenLanguage

/** The JSON editor of a custom server: a code editor with JSON coloring and [label] shown while it is empty. */
@Composable
internal fun JsonCodeEditor(
    label: String,
    state: CodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    SoraCodeEditor(
        state = state,
        language = ::jsonLanguage,
        modifier = modifier,
        readOnly = readOnly,
        placeholder = label,
    )
}

private fun jsonLanguage() = soraTokenLanguage(
    tokenize = ::tokenizeJsonLine,
    spanStart = { token -> token.start },
    colorId = { token -> token.kind.soraColorId() },
)

private fun JsonTokenKind.soraColorId(): Int = when (this) {
    JsonTokenKind.Normal -> EditorColorScheme.TEXT_NORMAL
    JsonTokenKind.Key -> EditorColorScheme.ATTRIBUTE_NAME
    JsonTokenKind.String -> EditorColorScheme.LITERAL
    JsonTokenKind.Number -> SyntaxNumberColorId
    JsonTokenKind.Literal -> SyntaxLiteralColorId
    JsonTokenKind.Punctuation -> EditorColorScheme.OPERATOR
}
