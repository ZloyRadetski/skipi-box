// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.skipi.ui.logs.SkipiLogViewerScreen
import app.skipi.ui.logs.exportText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText

@Composable
fun CoreLogsPage(
    padding: PaddingValues,
) {
    val services = LocalAppServices.current
    val context = LocalContext.current
    LogViewerPage(
        padding = padding,
        title = stringResource(R.string.core_logs_title),
        repository = services.coreLogRepository,
        onClear = { context.clearCoreLogFile(XrayLogFile.Error) },
    )
}

@Composable
fun AccessLogsPage(
    padding: PaddingValues,
) {
    val services = LocalAppServices.current
    val context = LocalContext.current
    LogViewerPage(
        padding = padding,
        title = stringResource(R.string.access_logs_title),
        repository = services.accessLogRepository,
        onClear = { context.clearCoreLogFile(XrayLogFile.Access) },
    )
}

@Composable
fun LogcatLogsPage(
    padding: PaddingValues,
) {
    val services = LocalAppServices.current
    LogViewerPage(
        padding = padding,
        title = stringResource(R.string.logcat_logs_title),
        repository = services.logcatRepository,
        showLogMetadata = true,
    )
}

@Composable
private fun LogViewerPage(
    padding: PaddingValues,
    title: String,
    repository: CoreLogRepository,
    showLogMetadata: Boolean = false,
    onClear: suspend () -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val services = LocalAppServices.current
    val tipNotifier = services.tipNotifier
    val logFileCreator = services.logFileCreator
    val scope = rememberCoroutineScope()
    var logEntries by remember(repository) { mutableStateOf(repository.entries.value) }
    val exportedMessage = stringResource(R.string.logs_exported)
    val exportFailedMessage = stringResource(R.string.logs_export_failed)

    DisposableEffect(repository) {
        val viewerLease = CoreLogTailerPollingPolicy.acquireViewer()
        onDispose { viewerLease.close() }
    }

    LaunchedEffect(repository) {
        // A tailer pushes new entries into this flow. Re-reading the complete log file every
        // 1.5 seconds made the viewer wake storage and rebuild its list while no line arrived.
        repository.refresh()
        repository.entries.collect { entries -> logEntries = entries }
    }

    SkipiLogViewerScreen(
        title = title,
        entries = logEntries,
        showMetadata = showLogMetadata,
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onBack = { navigator.pop() },
        onRefresh = {
            repository.refresh()
            logEntries = repository.entries.value
        },
        onClear = {
            onClear()
            repository.clear()
            logEntries = emptyList()
        },
        onExport = { exportEntries, exportTitle ->
            scope.launch {
                val uri = logFileCreator(logExportFileName(exportTitle)) ?: return@launch
                runCatching {
                    context.exportLogEntries(uri, exportEntries, showLogMetadata)
                }.onSuccess {
                    tipNotifier.show(exportedMessage)
                }.onFailure { error ->
                    tipNotifier.showError(error, exportFailedMessage)
                }
            }
        },
        onCopy = { text, confirmation ->
            scope.launch {
                clipboard.setPlainText(text)
                tipNotifier.show(confirmation)
            }
        },
    )
}

private suspend fun Context.exportLogEntries(
    uri: Uri,
    entries: List<CoreLogEntry>,
    showMetadata: Boolean,
) {
    withContext(Dispatchers.IO) {
        val outputStream = contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
        outputStream.writer(Charsets.UTF_8).use { writer ->
            entries.forEachIndexed { index, entry ->
                if (index > 0) writer.write("\n")
                writer.write(entry.exportText(showMetadata))
            }
        }
    }
}

private fun logExportFileName(title: String): String {
    val safeTitle = title
        .trim()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString("")
        .trim('-')
        .ifBlank { "logs" }
    return "skipi-$safeTitle-${System.currentTimeMillis()}.log"
}
