// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.LocalAppServices
import app.skipi.ui.components.StringListEditor as SharedStringListEditor
import app.skipi.ui.components.StringListStatusText as SharedStringListStatusText
import app.skipi.ui.feedback.LocalTipNotifier
import app.skipi.ui.feedback.TipNotifier
import kotlinx.coroutines.launch

@Composable
internal fun StringListEditor(
    editorKey: Any?,
    title: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    validateInput: (String) -> String? = { null },
    normalizeInput: (String) -> String = String::trim,
    onPendingChange: ((Boolean) -> Unit)? = null,
    headerActions: (@Composable () -> Unit)? = null,
    suggestionContent: (@Composable (onApplySuggestion: (String, Boolean) -> Unit) -> Unit)? = null,
) {
    val notifier = LocalAppServices.current.tipNotifier
    val notificationScope = rememberCoroutineScope()
    CompositionLocalProvider(
        LocalTipNotifier provides TipNotifier { message -> notificationScope.launch { notifier.show(message) } },
    ) {
        SharedStringListEditor(
            editorKey = editorKey,
            title = title,
            values = values,
            onValuesChange = onValuesChange,
            emptyText = emptyText,
            modifier = modifier,
            description = description,
            validateInput = validateInput,
            normalizeInput = normalizeInput,
            onPendingChange = onPendingChange,
            headerActions = headerActions,
            suggestionContent = suggestionContent,
        )
    }
}

@Composable
internal fun StringListStatusText(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) = SharedStringListStatusText(text, modifier, error)
