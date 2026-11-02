// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two buttons share a row only when the widest label, with the button's own horizontal padding,
 * fits in half the row. Judging by character count let a 12-letter "Восстановить" wrap onto two lines.
 */
class WarningConfirmDialogLayoutTest {
    // A 360 dp phone: 16 dp screen margin and 20 dp card padding on each side leave a 288 dp row.
    private val phoneRow = 288.dp

    @Test
    fun shortLabelsShareARow() {
        assertEquals(false, warningDialogStacksButtons(widestLabel = 60.dp, rowWidth = phoneRow))
    }

    @Test
    fun aLabelThatWouldWrapInAHalfWidthButtonIsStacked() {
        // Half of the row is 138 dp; 16 dp of padding on each side leaves 106 dp for the text.
        assertEquals(true, warningDialogStacksButtons(widestLabel = 110.dp, rowWidth = phoneRow))
        assertEquals(true, warningDialogStacksButtons(widestLabel = 200.dp, rowWidth = phoneRow))
    }

    @Test
    fun aLabelThatJustFitsStaysInTheRow() {
        assertEquals(false, warningDialogStacksButtons(widestLabel = 106.dp, rowWidth = phoneRow))
        assertEquals(true, warningDialogStacksButtons(widestLabel = 106.1.dp, rowWidth = phoneRow))
    }

    @Test
    fun aWiderRowFitsLongerLabels() {
        assertEquals(false, warningDialogStacksButtons(widestLabel = 150.dp, rowWidth = 420.dp))
    }
}
