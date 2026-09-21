// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Shared state policy for SKIPI's primary navigation pager.
 *
 * A host can still supply its own navigation chrome, but selection, repeated
 * Proxy taps and page animation are identical on Android and desktop.
 */
@Stable
class SkipiMainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    var proxyPageScrollToTopRequest by mutableIntStateOf(0)
        private set

    private var navJob: Job? = null

    fun animateTo(destination: SkipiMainDestination) {
        animateToPage(destination.pageIndex)
    }

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) {
            if (targetIndex == SkipiMainDestination.Proxy.pageIndex) {
                proxyPageScrollToTopRequest++
            }
            return
        }

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = tween(durationMillis = 280, easing = EaseInOut),
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberSkipiMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): SkipiMainPagerState = remember(pagerState, coroutineScope) {
    SkipiMainPagerState(pagerState, coroutineScope)
}
