// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeLocalError
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeStatusMessageTest {
    @Test
    fun localErrorsResolveToHostLocalizedLabels() {
        assertEquals(
            "Localized unavailable",
            proxyHomeLocalErrorMessage(
                ProxyHomeLocalError.Unavailable,
                unavailable = "Localized unavailable",
                variantUnavailable = "Localized option unavailable",
                operationFailed = "Localized operation failed",
            ),
        )
        assertEquals(
            "Localized option unavailable",
            proxyHomeLocalErrorMessage(
                ProxyHomeLocalError.VariantUnavailable,
                unavailable = "Localized unavailable",
                variantUnavailable = "Localized option unavailable",
                operationFailed = "Localized operation failed",
            ),
        )
        assertEquals(
            "Localized operation failed",
            proxyHomeLocalErrorMessage(
                ProxyHomeLocalError.OperationFailed,
                unavailable = "Localized unavailable",
                variantUnavailable = "Localized option unavailable",
                operationFailed = "Localized operation failed",
            ),
        )
    }
}
