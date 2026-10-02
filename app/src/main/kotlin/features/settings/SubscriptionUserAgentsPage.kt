// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.collectAppState
import app.skipi.ui.settings.SubscriptionUserAgentsScreen
import app.skipi.ui.settings.SubscriptionUserAgentsState

/** Android persistence/navigation adapter for the shared subscription User-Agent editor. */
@Composable
fun SubscriptionUserAgentsPage(padding: PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current

    SubscriptionUserAgentsScreen(
        state = SubscriptionUserAgentsState(values = appState.subscriptionUserAgents),
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onValuesChange = { values ->
            updateAppState { it.copy(subscriptionUserAgents = values) }
        },
        onBack = { navigator.pop() },
    )
}
