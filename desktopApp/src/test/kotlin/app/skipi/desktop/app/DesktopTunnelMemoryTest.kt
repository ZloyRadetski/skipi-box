// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopTunnelMemoryTest {
    @Test
    fun countsOnlyTheCoreHeapNotTheWholeApp() {
        val kb = readDesktopTunnelMemoryKb { Result.success("""{"allocBytes": 17825792, "sysBytes": 33554432}""") }

        assertEquals(17_408L, kb)
    }

    @Test
    fun showsNothingWhenTheCoreCannotAnswer() {
        assertEquals(0L, readDesktopTunnelMemoryKb { Result.failure(IllegalStateException("SKIPI Core has not been loaded")) })
        assertEquals(0L, readDesktopTunnelMemoryKb { Result.success("") })
        assertEquals(0L, readDesktopTunnelMemoryKb { Result.success("{}") })
    }
}
