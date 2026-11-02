// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeButtonSizeTest {
    @Test
    fun storedSettingValuesMapToTheFourSizes() {
        assertEquals(ProxyHomeButtonSize.Small, proxyHomeButtonSizeFromSetting(0))
        assertEquals(ProxyHomeButtonSize.Medium, proxyHomeButtonSizeFromSetting(1))
        assertEquals(ProxyHomeButtonSize.Large, proxyHomeButtonSizeFromSetting(2))
        assertEquals(ProxyHomeButtonSize.Giant, proxyHomeButtonSizeFromSetting(3))
    }

    @Test
    fun anUnknownValueKeepsTheSizeTheButtonHadBeforeTheSettingExisted() {
        assertEquals(ProxyHomeButtonSize.Large, proxyHomeButtonSizeFromSetting(-1))
        assertEquals(ProxyHomeButtonSize.Large, proxyHomeButtonSizeFromSetting(42))
    }
}
