// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigProfileRefreshMode
import app.skipi.app.config.TrafficConfigProfileRefreshOperations
import app.skipi.app.config.TrafficConfigProfileRefreshRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Ensures Desktop's ConfigProfile bridge uses the same manual-refresh decision as Android. */
class DesktopConfigProfileRefreshActionTest {
    @Test
    fun lockedProfileRequiresAndroidCompatibleConfirmationAndThenUnlocksCapturedSnapshot() {
        val profile = DesktopStoredConfig(
            id = 7,
            name = "Locked profile",
            content = "[General]\nloglevel = warning\n",
            sourceUrl = "https://example.test/profile.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 123L,
        )

        val confirmation = assertIs<TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh>(
            requestDesktopConfigProfileRefresh(profile),
        )

        assertEquals(profile.id, confirmation.snapshot.id)
        assertEquals(profile.name, confirmation.snapshot.name)
        assertEquals(profile.sourceUrl, confirmation.snapshot.sourceUrl)
        assertEquals(profile.updateLocked, confirmation.snapshot.updateLocked)
        assertEquals(profile.lastUpdatedAtMillis, confirmation.snapshot.lastUpdatedAtMillis)
        assertEquals(
            TrafficConfigProfileRefreshRequest.Refresh(
                snapshot = confirmation.snapshot,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = true),
            ),
            TrafficConfigProfileRefreshOperations.confirmUnlockAndRefresh(confirmation),
        )
    }

    @Test
    fun unlockedProfileUsesSharedRefreshRequestWithoutConfirmation() {
        val profile = DesktopStoredConfig(
            id = 8,
            name = "Unlocked profile",
            content = "[General]\nloglevel = warning\n",
            sourceUrl = "https://example.test/unlocked.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 456L,
        )

        val request = assertIs<TrafficConfigProfileRefreshRequest.Refresh>(
            requestDesktopConfigProfileRefresh(profile),
        )

        assertEquals(profile.id, request.snapshot.id)
        assertEquals(profile.name, request.snapshot.name)
        assertEquals(profile.sourceUrl, request.snapshot.sourceUrl)
        assertEquals(TrafficConfigProfileRefreshMode.Manual(unlock = false), request.mode)
    }

    @Test
    fun profileRefreshUsesTypedProfileUserAgentAndDesktopFallbackInsteadOfSettingsAgent() {
        val fallback = "Skipi/9.8.7/Desktop"
        val missingAgent = DesktopStoredConfig(
            id = 9,
            name = "Legacy profile",
            content = "[General]\nloglevel = warning\n",
            sourceUrl = "https://example.test/legacy.conf",
        )
        val explicitAgent = missingAgent.copy(
            id = 10,
            name = "Custom agent",
            content = "[General]\nloglevel = warning\n\n[SKIPI]\nresource-user-agent = Provider Agent/1.0\n",
        )
        val explicitBlankAgent = missingAgent.copy(
            id = 11,
            name = "Blank agent",
            content = "[General]\nloglevel = warning\n\n[SKIPI]\nresource-user-agent = \n",
        )

        assertEquals(
            fallback,
            assertIs<TrafficConfigProfileRefreshRequest.Refresh>(
                requestDesktopConfigProfileRefresh(missingAgent, resourceUserAgentFallback = fallback),
            ).snapshot.resourceSettings.userAgent,
        )
        assertEquals(
            "Provider Agent/1.0",
            assertIs<TrafficConfigProfileRefreshRequest.Refresh>(
                requestDesktopConfigProfileRefresh(explicitAgent, resourceUserAgentFallback = fallback),
            ).snapshot.resourceSettings.userAgent,
        )
        assertEquals(
            "",
            assertIs<TrafficConfigProfileRefreshRequest.Refresh>(
                requestDesktopConfigProfileRefresh(explicitBlankAgent, resourceUserAgentFallback = fallback),
            ).snapshot.resourceSettings.userAgent,
        )
    }
}
