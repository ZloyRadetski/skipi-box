// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.validation

import androidx.compose.runtime.Composable
import features.proxy.server.model.ProxyServerValidationIssue

@Composable
internal fun rememberProxyServerValidationMessageResolver(): (ProxyServerValidationIssue) -> String {
    return app.skipi.ui.server.validation.rememberProxyServerValidationMessageResolver()
}
