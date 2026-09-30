// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeLocalError

internal fun proxyHomeLocalErrorMessage(
    error: ProxyHomeLocalError,
    unavailable: String,
    variantUnavailable: String,
    operationFailed: String,
): String = when (error) {
    ProxyHomeLocalError.Unavailable -> unavailable
    ProxyHomeLocalError.VariantUnavailable -> variantUnavailable
    ProxyHomeLocalError.OperationFailed -> operationFailed
}
