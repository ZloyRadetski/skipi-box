// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.HTTP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProxyServerEditorControllerTest {
    @Test
    fun saveRequiresBasicValidationBeforeFullValidation() {
        val decision = ProxyServerEditorController.requestSave(HTTP(remarks = "test", server = "", port = "443"))
        val rejected = assertIs<ProxyServerEditorSaveDecision.Reject>(decision)
        assertFalse(rejected.fullValidation)
    }

    @Test
    fun optionalFullValidationIssuesRequireConfirmation() {
        val decision = ProxyServerEditorController.requestSave(HTTP(remarks = "test", server = "example.org", user = "user"))
        val confirm = assertIs<ProxyServerEditorSaveDecision.ConfirmFullValidation>(decision)
        assertTrue(confirm.issues.isNotEmpty())
    }

    @Test
    fun invalidAmneziaWgFullValidationCannotBeForceSaved() {
        val draft = AmneziaWg(
            remarks = "test",
            server = "example.org",
            secretKey = "key",
            publicKey = "key",
            s1 = "99999",
        )
        val rejected = assertIs<ProxyServerEditorSaveDecision.Reject>(ProxyServerEditorController.requestSave(draft))
        assertTrue(rejected.fullValidation)
    }

    @Test
    fun savePassesWhenBasicAndFullValidationAreClean() {
        assertEquals(
            ProxyServerEditorSaveDecision.Save,
            ProxyServerEditorController.requestSave(HTTP(remarks = "test", server = "example.org", port = "443")),
        )
    }

    @Test
    fun changeCheckUsesPersistedServerState() {
        val original = HTTP(remarks = "A", server = "example.org")
        assertFalse(ProxyServerEditorController.hasChanges(original, original.copy()))
        assertTrue(ProxyServerEditorController.hasChanges(original, original.copy(remarks = "B")))
    }

    @Test
    fun strategyMemberIdsKeepFirstSeenOrderWhileRemovingDuplicates() {
        assertEquals(listOf(7, 2, 9), ProxyServerEditorController.normalizeStrategyGroupMemberIds(listOf(7, 2, 7, 9, 2)))
    }
}
