// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.backup.AppBackupSettings
import kotlin.test.Test
import kotlin.test.assertFalse

class AppStateServerSearchDefaultTest {
    @Test
    fun serverSearchIsOffUnlessTheUserEnabledIt() {
        assertFalse(AppState().showServerSearch)
        assertFalse(AppBackupSettings().showServerSearch)
    }
}
