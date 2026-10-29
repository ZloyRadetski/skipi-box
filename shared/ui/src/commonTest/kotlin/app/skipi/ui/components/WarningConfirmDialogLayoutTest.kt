// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class WarningConfirmDialogLayoutTest {
    @Test
    fun shortLabelsShareARow() {
        assertEquals(false, warningDialogStacksButtons("Отмена", "Сбросить"))
        assertEquals(false, warningDialogStacksButtons("Cancel", "Delete"))
    }

    @Test
    fun longLabelsAreStackedSoTheyNeverWrapOnAPhone() {
        // "Остановить туннель" used to wrap onto three lines in a half-width button.
        assertEquals(true, warningDialogStacksButtons("Отмена", "Остановить туннель"))
        assertEquals(true, warningDialogStacksButtons("Не сохранять изменения", "Сохранить"))
    }
}
