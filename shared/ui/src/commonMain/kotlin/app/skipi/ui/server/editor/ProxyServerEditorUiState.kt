// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerValidationIssue
import features.proxy.server.model.decodePersistedProxyServer
import features.proxy.server.model.encodePersistedProxyServer

private val ProxyServerDraftSaver: Saver<ProxyServer<*>, String> = Saver(
    save = { it.encodePersistedProxyServer() },
    restore = { it.decodePersistedProxyServer() },
)

/** Portable persisted draft with the same original/server/result identity used by the Android page. */
@Composable
fun rememberSaveableProxyServerEditorDraft(
    original: ProxyServer<*>,
    serverId: Int?,
    resultKey: String?,
): ProxyServer<*> = rememberSaveable(original, serverId, resultKey, saver = ProxyServerDraftSaver) {
    original.editableCopy()
}

class ProxyServerEditorUiState internal constructor() {
    var pendingSaveIssues by mutableStateOf<List<ProxyServerValidationIssue>>(emptyList())
        private set
    var showDiscardConfirmation by mutableStateOf(false)
        private set

    val showFullValidationWarning: Boolean get() = pendingSaveIssues.isNotEmpty()

    fun showFullValidationWarning(issues: List<ProxyServerValidationIssue>) {
        pendingSaveIssues = issues
    }

    fun dismissFullValidationWarning() {
        pendingSaveIssues = emptyList()
    }

    fun requestDiscardConfirmation() {
        showDiscardConfirmation = true
    }

    fun dismissDiscardConfirmation() {
        showDiscardConfirmation = false
    }
}

@Composable
fun rememberProxyServerEditorUiState(vararg keys: Any?): ProxyServerEditorUiState = remember(*keys) { ProxyServerEditorUiState() }
