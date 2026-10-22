// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import app.skipi.app.config.TrafficConfigProfileRefreshMode
import app.skipi.app.config.TrafficConfigProfileRefreshOperations
import app.skipi.app.config.executeTrafficConfigAutoUpdate
import app.skipi.app.config.toTrafficConfigState
import features.config.TrafficConfigState

/** Bridges Desktop's latest-library persistence to the shared automatic profile refresh executor. */
internal class DesktopTrafficConfigAutoRefresh(
    private val readLibrary: () -> DesktopConfigLibrary,
    private val repository: DesktopTrafficConfigRepository,
    private val fetch: suspend (TrafficConfigState, String) -> String,
    private val reconcile: suspend () -> Unit,
    private val nowMillis: () -> Long,
    private val resourceUserAgentFallback: String,
) {
    suspend fun run(profileId: Int): TrafficConfigAutoUpdateExecutionResult {
        val capturedProfile = readLibrary().configs
            .firstOrNull { profile -> profile.id == profileId }
            ?.toTrafficConfigState(resourceUserAgentFallback)

        return executeTrafficConfigAutoUpdate(
            capturedProfile = capturedProfile,
            fetch = fetch,
            commit = { configId, normalizedDocument, capturedUrl ->
                repository.transformLatest { latestLibrary ->
                    val latestProfile = latestLibrary.configs.firstOrNull { profile -> profile.id == configId }
                        ?: return@transformLatest latestLibrary
                    val refreshedProfile = TrafficConfigProfileRefreshOperations.apply(
                        current = latestProfile.toTrafficConfigState(resourceUserAgentFallback),
                        normalizedDocument = normalizedDocument,
                        capturedUrl = capturedUrl,
                        timestamp = nowMillis(),
                        mode = TrafficConfigProfileRefreshMode.Automatic,
                    )
                    DesktopConfigLibraries.update(
                        library = latestLibrary,
                        id = configId,
                        name = refreshedProfile.name,
                        content = refreshedProfile.rawConfig,
                        sourceUrl = refreshedProfile.sourceUrl,
                        updateLocked = refreshedProfile.updateLocked,
                        lastUpdatedAtMillis = refreshedProfile.lastUpdatedAtMillis,
                    )
                }
            },
            reconcile = reconcile,
        )
    }
}
