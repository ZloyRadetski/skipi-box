// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopConfigLibraryActiveProfileTest {
    private fun library(selected: Int?, vararg profiles: Pair<Int, String>) = DesktopConfigLibrary(
        selectedConfigId = selected,
        configs = profiles.map { (id, content) -> DesktopStoredConfig(id = id, name = "p$id", content = content) },
        nextConfigId = 100,
    )

    @Test
    fun activeProfileIsTheSelectedOneOrNull() {
        assertEquals(2, library(2, 1 to "a", 2 to "b").activeProfile?.id)
        assertNull(library(null, 1 to "a").activeProfile)
        assertNull(library(9, 1 to "a").activeProfile)
    }

    @Test
    fun activeProfileChangesWhenSelectionOrContentChanges() {
        val before = library(1, 1 to "a", 2 to "b")
        assertFalse(before.activeProfileChangedFrom(before))
        assertTrue(library(2, 1 to "a", 2 to "b").activeProfileChangedFrom(before))
        assertTrue(library(1, 1 to "edited", 2 to "b").activeProfileChangedFrom(before))
        assertFalse(library(1, 1 to "a", 2 to "other").activeProfileChangedFrom(before))
    }
}
