// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.skipi.ui.server.editor.ConfTokenKind
import app.skipi.ui.server.editor.tokenizeConfLine
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import ui.editor.CodeEditorState
import ui.editor.SoraCodeEditor
import ui.editor.SyntaxCommentColorId
import ui.editor.SyntaxLiteralColorId
import ui.editor.SyntaxNumberColorId
import ui.editor.soraTokenLanguage

/** The raw profile editor: a code editor that colors sections, keys, comments and values. */
@Composable
internal fun ConfCodeEditor(
    state: CodeEditorState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    SoraCodeEditor(state = state, language = ::confLanguage, modifier = modifier, readOnly = readOnly)
}

private fun confLanguage() = soraTokenLanguage(
    tokenize = ::tokenizeConfLine,
    spanStart = { token -> token.start },
    colorId = { token -> token.kind.soraColorId() },
)

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
