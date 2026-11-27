// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlin.test.Test
import kotlin.test.assertEquals

class MultilinkLinksReporterTest {
    private fun link(id: String, handle: Long = 1) = MultilinkHostLink(id = id, name = id, handle = handle)

    @Test
    fun anUnchangedListIsPushedOnce() {
        val pushed = mutableListOf<List<MultilinkHostLink>>()
        val reporter = MultilinkLinksReporter { pushed += it; Result.success(Unit) }
        reporter.report(listOf(link("wifi")))
        reporter.report(listOf(link("wifi")))
        reporter.report(listOf(link("wifi", handle = 2)))
        assertEquals(2, pushed.size)
    }

    @Test
    fun nothingIsPushedAfterClose() {
        val pushed = mutableListOf<List<MultilinkHostLink>>()
        val reporter = MultilinkLinksReporter { pushed += it; Result.success(Unit) }
        reporter.close()
        reporter.report(listOf(link("wifi")))
        assertEquals(0, pushed.size)
    }

    @Test
    fun aRefusedPushIsRetriedWithTheSameList() {
        var fail = true
        var calls = 0
        val reporter = MultilinkLinksReporter { calls++; if (fail) Result.failure(IllegalStateException()) else Result.success(Unit) }
        reporter.report(listOf(link("wifi")))
        fail = false
        reporter.report(listOf(link("wifi")))
        reporter.report(listOf(link("wifi")))
        assertEquals(2, calls)
    }
}
