// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionRefreshMessageTest {
    @Test
    fun successfulLoadsUseTheirGroupAndServerCounts() {
        assertEquals(
            "ok 2/3",
            formatSubscriptionRefreshMessage(
                summary = SubscriptionRefreshMessageSummary(
                    updatedGroupCount = 2,
                    failedGroupCount = 0,
                    importedServerCount = 3,
                    firstFailureMessage = null,
                ),
                successTemplate = "ok {groupCount}/{serverCount}",
                failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
            ),
        )
    }

    @Test
    fun partialFailureUsesCountsAndAppendsTheFirstFailure() {
        assertEquals(
            "partial 1/2/2: Timeout connecting to server",
            formatSubscriptionRefreshMessage(
                summary = SubscriptionRefreshMessageSummary(
                    updatedGroupCount = 1,
                    failedGroupCount = 2,
                    importedServerCount = 2,
                    firstFailureMessage = "Timeout connecting to server",
                ),
                successTemplate = "ok {groupCount}/{serverCount}",
                failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
            ),
        )
    }

    @Test
    fun allFailuresShowOnlyTheFirstFailureMessage() {
        assertEquals(
            "DNS error: provider.example",
            formatSubscriptionRefreshMessage(
                summary = SubscriptionRefreshMessageSummary(
                    updatedGroupCount = 0,
                    failedGroupCount = 2,
                    importedServerCount = 0,
                    firstFailureMessage = "DNS error: provider.example",
                ),
                successTemplate = "ok {groupCount}/{serverCount}",
                failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
            ),
        )
    }

    @Test
    fun partialFailureWithoutTextKeepsOnlyTheLocalizedTemplate() {
        assertEquals(
            "partial 1/1/0",
            formatSubscriptionRefreshMessage(
                summary = SubscriptionRefreshMessageSummary(
                    updatedGroupCount = 1,
                    failedGroupCount = 1,
                    importedServerCount = 0,
                    firstFailureMessage = "  ",
                ),
                successTemplate = "ok {groupCount}/{serverCount}",
                failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
            ),
        )
    }
}
