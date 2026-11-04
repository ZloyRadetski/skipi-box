// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.ui.config.RoutingImportSource
import app.skipi.ui.config.SkipiRoutingImportDialog
import app.skipi.ui.config.SkipiRoutingTableState
import app.skipi.ui.config.SkipiTrafficConfigFullScreenScaffold
import app.skipi.ui.config.SkipiTrafficConfigRoutingTable
import app.skipi.ui.config.SkipiTrafficConfigUrlImportDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.routing_import_no_valid_rules
import app.skipi.ui.resources.routing_imported
import app.skipi.ui.resources.routing_table_import_failed
import app.skipi.ui.resources.routing_table_import_unwritable
import app.skipi.ui.text.formatTemplate
import features.config.ShadowrocketRule
import features.config.TrafficConfigState
import features.config.V2rayRoutingImport
import features.config.parseV2rayRoutingImportOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Where the table reads a v2rayN rule export from: null means nothing was chosen or there was nothing to read. */
internal class DesktopRoutingImportSources(
    val readFile: suspend () -> String?,
    val readClipboard: suspend () -> String?,
    val fetchUrl: suspend (String) -> String,
) {
    companion object {
        fun system(fetchUrl: suspend (String) -> String) = DesktopRoutingImportSources(
            readFile = { withContext(Dispatchers.IO) { chooseRoutingExportFile() } },
            readClipboard = { withContext(Dispatchers.IO) { readRoutingExportClipboard() } },
            fetchUrl = fetchUrl,
        )
    }
}

/** The routing table as a full-screen page of a profile, with the import of v2rayN rules. */
@Composable
internal fun DesktopRoutingTableScreen(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    state: SkipiRoutingTableState,
    sources: DesktopRoutingImportSources,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onPickPolicy: (current: String) -> Unit,
    onEditRule: (ShadowrocketRule) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<V2rayRoutingImport?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var urlDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    val noRules = stringResource(Res.string.routing_import_no_valid_rules)
    val importedTemplate = stringResource(Res.string.routing_imported)
    val unwritableTemplate = stringResource(Res.string.routing_table_import_unwritable)
    val failedTemplate = stringResource(Res.string.routing_table_import_failed)

    fun offer(text: String?) {
        val import = text?.trimStart('﻿')?.parseV2rayRoutingImportOrNull()
        if (import == null || (import.rows.isEmpty() && import.finalPolicy == null)) {
            notice = noRules
        } else {
            notice = null
            pending = import
        }
    }

    fun failed(error: Throwable) {
        notice = failedTemplate.formatTemplate("reason" to (error.message ?: error::class.simpleName.orEmpty()))
    }

    fun import(source: RoutingImportSource) {
        when (source) {
            RoutingImportSource.Url -> urlDialog = true
            RoutingImportSource.File -> scope.launch {
                runCatching { sources.readFile() }
                    .onSuccess { text -> if (text != null) offer(text) }
                    .onFailure { error -> if (error is CancellationException) throw error else failed(error) }
            }
            RoutingImportSource.Clipboard -> scope.launch {
                runCatching { sources.readClipboard() }
                    .onSuccess(::offer)
                    .onFailure { error -> if (error is CancellationException) throw error else failed(error) }
            }
        }
    }

    SkipiTrafficConfigFullScreenScaffold(
        title = stringResource(Res.string.configs_rules_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = onBack,
    ) { _, listPadding, _ ->
        SkipiTrafficConfigRoutingTable(
            profile = profile,
            controller = controller,
            state = state,
            contentPadding = listPadding,
            onPickPolicy = onPickPolicy,
            onEditRule = onEditRule,
            onImport = ::import,
            notice = notice,
        )
    }

    SkipiRoutingImportDialog(
        import = pending,
        onDismissRequest = { pending = null },
        onMode = { import, mode ->
            pending = null
            val result = controller.importRoutingRows(profile.id, import, mode)
            notice = when {
                result.result != TrafficConfigEditorResult.Saved -> null
                result.unwritable.isNotEmpty() ->
                    importedTemplate.formatTemplate("count" to result.added) + ". " +
                        unwritableTemplate.formatTemplate("values" to result.unwritable.joinToString(", ") { it.value })
                else -> importedTemplate.formatTemplate("count" to result.added)
            }
            state.close()
        },
    )
    SkipiTrafficConfigUrlImportDialog(
        show = urlDialog,
        url = url,
        onUrlChange = { url = it },
        onDismissRequest = { urlDialog = false },
        onImport = { address ->
            urlDialog = false
            url = ""
            scope.launch {
                runCatching { sources.fetchUrl(address.trim()) }
                    .onSuccess(::offer)
                    .onFailure { error -> if (error is CancellationException) throw error else failed(error) }
            }
        },
    )
}

private const val MaxRoutingExportBytes = 8 * 1024 * 1024

private fun readRoutingExportClipboard(): String? {
    val text = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
    return text?.takeIf { it.isNotBlank() && it.toByteArray(StandardCharsets.UTF_8).size <= MaxRoutingExportBytes }
}

private fun chooseRoutingExportFile(): String? {
    val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val file = Path.of(dialog.directory.orEmpty(), name).normalize()
    require(Files.isRegularFile(file)) { "Not a file" }
    require(Files.size(file) <= MaxRoutingExportBytes) { "The file is larger than ${MaxRoutingExportBytes / 1024 / 1024} MB" }
    return Files.readString(file, StandardCharsets.UTF_8)
}
