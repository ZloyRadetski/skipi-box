// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionGroupDraftValidationTest {
    @Test
    fun manualGroupMayLeaveUrlBlank() {
        assertNull(validateSubscriptionGroupDraft(url = "", updateInterval = "24"))
    }

    @Test
    fun rejectsInvalidUrlBeforeRequestingHttpConfirmation() {
        assertEquals(
            SubscriptionGroupDraftIssue.InvalidUrl,
            validateSubscriptionGroupDraft(url = "http://bad host/path", updateInterval = "24"),
        )
    }

    @Test
    fun asksForConfirmationForValidPlainHttpUrl() {
        assertEquals(
            SubscriptionGroupDraftIssue.InsecureHttpConfirmationRequired,
            validateSubscriptionGroupDraft(url = "http://example.com/sub", updateInterval = "24"),
        )
        assertNull(
            validateSubscriptionGroupDraft(
                url = "http://example.com/sub",
                updateInterval = "24",
                allowInsecureHttp = true,
            ),
        )
    }

    @Test
    fun invalidIntervalTakesPrecedence() {
        assertEquals(
            SubscriptionGroupDraftIssue.InvalidInterval,
            validateSubscriptionGroupDraft(url = "not a url", updateInterval = "24..5"),
        )
    }
}
