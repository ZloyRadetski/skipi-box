// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionInstallMessageTest {
    @Test
    fun install_message_adds_saved_title_before_refresh_result_for_duplicate() {
        val message = formatSubscriptionInstallMessage(
            existingTitle = "Saved subscription",
            refreshMessage = "Updated 1 group with 3 servers",
            existingUrlTemplate = "This URL already belongs to {name}.",
        )

        assertEquals(
            "This URL already belongs to Saved subscription.\nUpdated 1 group with 3 servers",
            message,
        )
    }

    @Test
    fun new_install_message_returns_refresh_result_without_duplicate_notice() {
        val message = formatSubscriptionInstallMessage(
            existingTitle = null,
            refreshMessage = "Subscription updated",
            existingUrlTemplate = "This URL already belongs to {name}.",
        )

        assertEquals("Subscription updated", message)
    }
}
