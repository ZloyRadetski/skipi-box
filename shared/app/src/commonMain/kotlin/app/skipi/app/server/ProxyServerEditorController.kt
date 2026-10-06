// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerValidationIssue
import features.proxy.server.model.encodePersistedProxyServer

/** Result of validating a proxy-server draft before a save. */
sealed interface ProxyServerEditorSaveDecision {
    data class Reject(val issue: ProxyServerValidationIssue, val fullValidation: Boolean) : ProxyServerEditorSaveDecision
    data class ConfirmFullValidation(val issues: List<ProxyServerValidationIssue>) : ProxyServerEditorSaveDecision
    data object Save : ProxyServerEditorSaveDecision
}

/** Shared save/discard policy; hosts retain draft persistence and navigation ownership. */
object ProxyServerEditorController {
    /** Removes duplicate membership IDs while preserving their original order. */
    fun normalizeStrategyGroupMemberIds(serverIds: Iterable<Int>): List<Int> = serverIds.distinct()

    fun hasChanges(original: ProxyServer<*>, draft: ProxyServer<*>): Boolean = try {
        original.encodePersistedProxyServer() != draft.encodePersistedProxyServer()
    } catch (_: Throwable) {
        original != draft
    }

    fun requestSave(draft: ProxyServer<*>): ProxyServerEditorSaveDecision {
        draft.validateBasic().firstOrNull()?.let {
            return ProxyServerEditorSaveDecision.Reject(it, fullValidation = false)
        }
        val fullIssues = draft.validateFull()
        if (fullIssues.isEmpty()) return ProxyServerEditorSaveDecision.Save
        if (draft is AmneziaWg) return ProxyServerEditorSaveDecision.Reject(fullIssues.first(), fullValidation = true)
        return ProxyServerEditorSaveDecision.ConfirmFullValidation(fullIssues)
    }
}
