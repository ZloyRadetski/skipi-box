// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.skipi.ui.logs.SkipiLogViewerScreen
import app.skipi.ui.logs.exportText
import features.logs.CoreLogEntry
import features.logs.CoreLogRepository
import features.logs.CoreLogTailerPollingPolicy
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** Desktop host of the shared log viewer for one repository. */
@Composable
internal fun DesktopLogViewer(
    title: String,
    repository: CoreLogRepository,
    padding: PaddingValues,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var entries by remember(repository) { mutableStateOf(repository.entries.value) }

    DisposableEffect(repository) {
        val viewerLease = CoreLogTailerPollingPolicy.acquireViewer()
        onDispose { viewerLease.close() }
    }
    LaunchedEffect(repository) {
        repository.refresh()
        repository.entries.collect { current -> entries = current }
    }

    SkipiLogViewerScreen(
        title = title,
        entries = entries,
        showMetadata = false,
        padding = padding,
        isWideScreen = true,
        onBack = onBack,
        onRefresh = {
            repository.refresh()
            entries = repository.entries.value
        },
        onClear = {
            onClear()
            entries = emptyList()
        },
        onExport = { exportEntries, exportTitle ->
            exportDesktopLogEntries(exportEntries, exportTitle)
                .onSuccess { saved -> if (saved) onMessage("Журнал сохранён.") }
                .onFailure { error -> onMessage("Не удалось сохранить журнал: ${error.message.orEmpty()}") }
        },
        onCopy = { text, confirmation ->
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            }.onSuccess { onMessage(confirmation) }
        },
    )
}

/** Asks for a target file and writes the entries; false means the user cancelled. */
internal fun exportDesktopLogEntries(entries: List<CoreLogEntry>, title: String): Result<Boolean> = runCatching {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = "skipi-${title.filter(Char::isLetterOrDigit).ifBlank { "logs" }}-${System.currentTimeMillis()}.log"
    dialog.isVisible = true
    val name = dialog.file ?: return@runCatching false
    File(dialog.directory, name).writeText(entries.joinToString("\n") { it.exportText(showMetadata = false) }, Charsets.UTF_8)
    true
}
