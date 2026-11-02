// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppUpdateChannelTest {
    @Test
    fun stableIsTheDefaultForMissingOrUnknownStoredValues() {
        assertEquals(AppUpdateChannel.Stable, AppUpdateChannel.fromId(null))
        assertEquals(AppUpdateChannel.Stable, AppUpdateChannel.fromId(""))
        assertEquals(AppUpdateChannel.Stable, AppUpdateChannel.fromId("beta-from-the-future"))
    }

    @Test
    fun storedIdsRoundTripAndStayStable() {
        assertEquals("stable", AppUpdateChannel.Stable.id)
        assertEquals("nightly", AppUpdateChannel.Nightly.id)
        AppUpdateChannel.entries.forEach { channel ->
            assertEquals(channel, AppUpdateChannel.fromId(channel.id))
        }
    }

    @Test
    fun stableAsksForTheLatestReleaseAndNightlyForTheReleaseList() {
        assertEquals(
            "https://api.github.com/repos/ZloyRadetski/skipi-box/releases/latest",
            AppUpdateChannel.Stable.releasesUrl,
        )
        assertEquals(
            "https://api.github.com/repos/ZloyRadetski/skipi-box/releases?per_page=20",
            AppUpdateChannel.Nightly.releasesUrl,
        )
        assertNotEquals(AppUpdateChannel.Stable.releasesUrl, AppUpdateChannel.Nightly.releasesUrl)
    }
}
