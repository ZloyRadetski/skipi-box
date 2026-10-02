// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalProxySettingsValuesTest {
    @Test
    fun sanitizesPortInputToFiveDigits() {
        assertEquals("12345", sanitizeLocalProxyPort("1a2-34567"))
        assertEquals("", sanitizeLocalProxyPort("port"))
    }

    @Test
    fun validatesPortRange() {
        assertTrue(isValidLocalProxyPort("1"))
        assertTrue(isValidLocalProxyPort("65535"))
        assertFalse(isValidLocalProxyPort("0"))
        assertFalse(isValidLocalProxyPort("65536"))
        assertFalse(isValidLocalProxyPort(""))
    }
}
