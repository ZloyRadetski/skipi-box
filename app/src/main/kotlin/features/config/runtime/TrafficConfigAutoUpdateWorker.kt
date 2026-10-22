// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import data.repository.reconcileTrafficConfigProxyGroups
import features.config.withCommittedTrafficConfigRefresh
import app.skipi.app.config.TrafficConfigProfileRefreshMode
import features.subscription.normalizeSkipiUserAgent
import features.subscription.usecase.toSubscriptionFetchOptions

internal class TrafficConfigAutoUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val configId = inputData.getInt(TrafficConfigIdKey, -1)
        if (configId == -1) return Result.success()
        val application = applicationContext as? SkipiApplication ?: return Result.failure()
        val config = application.stateStore.state.value.trafficConfigs.firstOrNull { it.id == configId }
        return when (
            executeTrafficConfigAutoUpdate(
                capturedProfile = config,
                fetch = { profile, url ->
                    val state = application.stateStore.state.value
                    application.subscriptionFetcher.fetch(
                        url = url,
                        userAgent = normalizeSkipiUserAgent(profile.resourceSettings.userAgent),
                        // Background refreshes must traverse the running tunnel/proxy
                        // instead of the direct path, which may be blocked.
                        options = state.toSubscriptionFetchOptions(useRunningProxy = state.proxyRunning),
                    )
                },
                commit = { profileId, normalizedDocument, capturedUrl ->
                    application.stateStore.update { state ->
                        state.withCommittedTrafficConfigRefresh(
                            configId = profileId,
                            normalizedDocument = normalizedDocument,
                            capturedUrl = capturedUrl,
                            timestamp = System.currentTimeMillis(),
                            mode = TrafficConfigProfileRefreshMode.Automatic,
                        )
                    }
                },
                reconcile = { application.stateStore.reconcileTrafficConfigProxyGroups() },
            )
        ) {
            TrafficConfigAutoUpdateExecutionResult.Success -> Result.success()
            TrafficConfigAutoUpdateExecutionResult.Retry -> Result.retry()
        }
    }
}
