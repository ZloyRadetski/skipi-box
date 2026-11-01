// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * While the keyboard is open for the JSON text the name field and the override switch step aside,
 * so the editor gets the whole screen; typing the name itself must never hide that field.
 */
class CustomEditorPropertiesVisibilityTest {
    @Test
    fun propertiesStayWhenThereIsNoKeyboard() {
        assertTrue(customEditorPropertiesVisible(imeVisible = false, remarksFocused = false))
    }

    @Test
    fun propertiesStepAsideWhileTheKeyboardServesTheJsonText() {
        assertFalse(customEditorPropertiesVisible(imeVisible = true, remarksFocused = false))
    }

    @Test
    fun theNameFieldStaysWhileItIsBeingTyped() {
        assertTrue(customEditorPropertiesVisible(imeVisible = true, remarksFocused = true))
    }
}
