// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateDefaultsTest {
    @Test
    fun default_routes_are_empty() {
        assertTrue(DefaultRouteRules.isEmpty())
    }
}
