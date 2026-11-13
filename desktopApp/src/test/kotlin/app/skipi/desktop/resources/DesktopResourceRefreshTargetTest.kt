// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileSourceV2FlyGithub
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopResourceRefreshTargetTest {
    @Test
    fun theRefreshTargetCarriesWhenTheProfilesGeoFilesWereLastFetched() {
        val profile = TrafficConfigState(
            id = 3,
            name = "Profile",
            rawConfig = "",
            resourceSettings = TrafficConfigResourceSettings(source = ResourceFileSourceV2FlyGithub),
        )

        assertEquals(
            777L,
            profile.toDesktopTrafficConfigRefreshTarget(resourceLastUpdatedAtMillis = 777L).resourceLastUpdatedAtMillis,
        )
        assertEquals(0L, profile.toDesktopTrafficConfigRefreshTarget().resourceLastUpdatedAtMillis)
    }
}
