// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import app.AppState
import features.updater.AppUpdateInfo
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateCoordinatorTest {
    private val update = AppUpdateInfo(
        versionName = "1.2.3",
        versionCode = 123,
        releaseTitle = "Release",
        changelog = "Changes",
        downloadUrl = "https://example.invalid/SKIPI.apk",
        assetName = "SKIPI.apk",
        apkSizeBytes = 10L,
        publishedAt = "2026-09-15T00:00:00Z",
    )

    @Test
    fun release_identity_ignores_release_notes_but_rejects_a_different_asset() {
        assertTrue(update.isSameReleaseAs(update.copy(changelog = "Corrected notes")))
        assertFalse(update.isSameReleaseAs(update.copy(assetName = "SKIPI-arm64.apk")))
    }

    @Test
    fun stale_worker_can_only_update_its_current_release() {
        val state = AppState(availableAppUpdate = update)
        assertTrue(state.matchesUpdate(update))
        assertFalse(state.matchesUpdate(update.copy(versionCode = 124, versionName = "1.2.4")))
    }
}
