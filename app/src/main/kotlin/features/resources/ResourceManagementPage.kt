// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import app.AppState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.collectAppState
import app.skipi.ui.resourcefiles.ResourceManagementScreen
import features.config.withSkipiSettingsInRawConfig
import features.config.withUpdatedTrafficConfig
import engine.network.toPortOrNull
import java.text.DateFormat
import java.util.Date

@Composable
fun ResourceManagementPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val profile = appState.trafficConfigs.firstOrNull { config -> config.id == trafficConfigId } ?: return
    val services = LocalAppServices.current
    val navigator = LocalNavigator.current
    val updateAppState = LocalUpdateAppState.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    ResourceManagementScreen(
        profile = profile,
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        queue = services.resourceFileUpdateCoordinator,
        operations = services.resourceFileUseCase,
        updateOptionsForUserAgent = { userAgent -> appState.resourceFileUpdateOptions(userAgent) },
        userAgentPresets = appState.subscriptionUserAgents,
        deletionConfirmationEnabled = appState.enableDeletionConfirmation,
        actionScope = services.appScope,
        onBack = { navigator.pop() },
        updateSettings = { transform ->
            updateAppState { state ->
                state.withUpdatedTrafficConfig(trafficConfigId) { config ->
                    config.copy(
                        resourceSettings = transform(config.resourceSettings),
                    ).withSkipiSettingsInRawConfig()
                }
            }
        },
        formatTimestamp = { timestamp ->
            if (timestamp > 0L) dateFormat.format(Date(timestamp)) else "-"
        },
        onTip = { message -> services.tipNotifier.show(message) },
        onError = { error -> services.tipNotifier.showError(error) },
    )
}

internal fun AppState.resourceFileUpdateOptions(userAgent: String): ResourceFileUpdateOptions =
    ResourceFileUpdateOptions(
        useRunningProxy = proxyRunning,
        fallbackProxyPort = localProxyPort.toPortOrNull(),
        fallbackProxyUsername = localProxyUsername,
        fallbackProxyPassword = localProxyPassword,
        userAgent = userAgent,
        showNotifications = enableResourceFileNotifications,
    )
