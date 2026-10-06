// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.skipi.ui.settings.SkipiSubscriptionPingSettingsScreen
import app.skipi.ui.settings.SubscriptionPingPageState
import kotlinx.coroutines.launch

@Composable
fun SubscriptionPingSettingsPage(padding: androidx.compose.foundation.layout.PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val selectedTestToastTemplate = stringResource(R.string.subscription_ping_selected_test)

    SkipiSubscriptionPingSettingsScreen(
        state = SubscriptionPingPageState(
            mode = appState.subscriptionPingMode,
            concurrency = appState.subscriptionPingConcurrency,
            timeoutMillis = appState.subscriptionPingTimeoutMillis,
            url = appState.subscriptionPingUrl,
        ),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onModeChange = { mode -> updateAppState { it.copy(subscriptionPingMode = mode) } },
        onConcurrencyChange = { concurrency -> updateAppState { it.copy(subscriptionPingConcurrency = concurrency) } },
        onTimeoutMillisChange = { timeout -> updateAppState { it.copy(subscriptionPingTimeoutMillis = timeout) } },
        onUrlChange = { url -> updateAppState { it.copy(subscriptionPingUrl = url.trim()) } },
        onPresetSelected = { name ->
            scope.launch { tipNotifier.show(String.format(selectedTestToastTemplate, name)) }
        },
    )
}
