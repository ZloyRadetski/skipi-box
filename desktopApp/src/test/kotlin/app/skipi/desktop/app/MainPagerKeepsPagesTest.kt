// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.skipi.ui.navigation.SkipiMainDestination
import app.skipi.ui.navigation.SkipiMainPager
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Going from Settings to Home used to compose Home for the first time inside the page animation, which dropped frames.
 * The top-level pages stay composed, so a switch only slides what is already there.
 */
class MainPagerKeepsPagesTest {
    @Test
    fun everyTopLevelPageStaysComposedWhileSettingsIsOpen() {
        val composed = mutableSetOf<SkipiMainDestination>()
        val pagerState = PagerState(currentPage = SkipiMainDestination.Settings.pageIndex) { SkipiMainDestination.entries.size }
        val scene = ImageComposeScene(width = 300, height = 300, density = Density(1f)) {
            SkipiMainPager(pagerState = pagerState) { destination ->
                composed += destination
            }
        }
        try {
            scene.render()
            scene.render(100_000_000L)
        } finally {
            scene.close()
        }

        assertTrue(SkipiMainDestination.Proxy in composed, "Home must already be composed when Settings is shown: $composed")
        assertTrue(SkipiMainDestination.Configs in composed, "Configs must already be composed: $composed")
    }
}
