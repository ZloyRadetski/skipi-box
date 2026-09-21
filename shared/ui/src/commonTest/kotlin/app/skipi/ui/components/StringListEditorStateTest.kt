// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringListEditorStateTest {

    @Test
    fun testHasPendingStringListEdit() {
        assertFalse(hasPendingStringListEdit("", -1))
        assertFalse(hasPendingStringListEdit("   ", -1))
        assertTrue(hasPendingStringListEdit("example.com", -1))
        assertTrue(hasPendingStringListEdit("", 0))
        assertTrue(hasPendingStringListEdit("", 2))
        assertTrue(hasPendingStringListEdit("test", 1))
    }
}
