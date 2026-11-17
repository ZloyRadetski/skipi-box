// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.runtime.Composable
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.subscription_ping_settings_summary
import app.skipi.ui.text.formatTemplate
import org.jetbrains.compose.resources.stringResource

/** The line under "ping" in the subscription settings, in the language of the app: the URL and the timeout. */
@Composable
fun subscriptionPingSettingsSummary(url: String, timeoutMillis: String): String =
    stringResource(Res.string.subscription_ping_settings_summary).formatTemplate(
        "url" to url,
        "timeout" to timeoutMillis,
    )
