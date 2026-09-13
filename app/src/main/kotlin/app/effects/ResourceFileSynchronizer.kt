// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import data.AndroidAppStateStore
import app.activeTrafficConfig
import features.logs.AndroidAppLogger
import features.resources.ResourceFileUseCase
import features.resources.runtime.XrayResourceFileScope

@Composable
internal fun ResourceFileSynchronizer(
    resourceFileUseCase: ResourceFileUseCase,
    stateStore: AndroidAppStateStore,
) {
    LaunchedEffect(resourceFileUseCase, stateStore) {
        runCatching {
            val activeConfig = stateStore.state.value.activeTrafficConfig()
            resourceFileUseCase.synchronizeBundledFilesAfterPackageUpdate(
                scope = XrayResourceFileScope(
                    trafficConfigId = activeConfig?.id ?: 1,
                    resourceFileSource = activeConfig?.resourceSettings?.source ?: 0,
                ),
            )
        }
            .onFailure { error ->
                AndroidAppLogger.warn(
                    LogTag,
                    "Failed to synchronize bundled resource files",
                    error,
                )
            }
    }
}

private const val LogTag = "ResourceFileSync"
