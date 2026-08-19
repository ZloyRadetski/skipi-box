// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import data.AndroidAppStateStore
import app.activeTrafficConfig
import features.logs.AndroidAppLogger
import features.resources.ResourceFileUseCase

@Composable
internal fun ResourceFileSynchronizer(
    resourceFileUseCase: ResourceFileUseCase,
    stateStore: AndroidAppStateStore,
) {
    LaunchedEffect(resourceFileUseCase, stateStore) {
        runCatching {
            resourceFileUseCase.synchronizeBundledFilesAfterPackageUpdate(
                resourceFileSource = stateStore.state.value.activeTrafficConfig()
                    ?.resourceSettings
                    ?.source
                    ?: 0,
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
