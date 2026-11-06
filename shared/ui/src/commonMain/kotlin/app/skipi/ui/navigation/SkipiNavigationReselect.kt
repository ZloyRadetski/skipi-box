// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * Counts how many times the navigation item of the section on screen was tapped while that section was
 * already open. The host raises it; a page reacts to a change through [OnNavigationReselect].
 */
val LocalNavigationReselectToken = compositionLocalOf { 0 }

/**
 * Runs [action] each time the open section's navigation item is tapped again, for a page to scroll to
 * its top. A page that only appears after such a tap does not react to that one.
 */
@Composable
fun OnNavigationReselect(action: suspend () -> Unit) {
    val token = LocalNavigationReselectToken.current
    val seenWhenShown = remember { token }
    LaunchedEffect(token) {
        if (token != seenWhenShown) action()
    }
}
