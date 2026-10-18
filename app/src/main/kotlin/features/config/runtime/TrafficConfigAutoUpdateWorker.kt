// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import data.repository.reconcileTrafficConfigProxyGroups
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.config.withCommittedTrafficConfigRefresh
import app.skipi.app.config.TrafficConfigProfileRefreshMode
import features.subscription.normalizeSkipiUserAgent
import features.subscription.runtime.AndroidSubscriptionFetchOptions
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
            ?: return Result.success()
        if (!config.autoUpdate || config.updateLocked || config.sourceUrl.isBlank()) {
            return Result.success()
        }
        return runCatching {
            val url = config.sourceUrl.trim()
            val state = application.stateStore.state.value
            val fetched = application.subscriptionFetcher.fetch(
                url = url,
                userAgent = normalizeSkipiUserAgent(config.resourceSettings.userAgent),
                // Background refreshes must traverse the running tunnel/proxy
                // instead of the direct path, which may be blocked.
                options = state.toSubscriptionFetchOptions(useRunningProxy = state.proxyRunning),
            )
            val normalized = fetched.trimEnd() + "\n"
            val analysis = normalized.analyzeShadowrocketConfig()
            if (analysis.diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
                return@runCatching Result.retry()
            }
            application.stateStore.update { state ->
                state.withCommittedTrafficConfigRefresh(
                    configId = config.id,
                    normalizedDocument = normalized,
                    capturedUrl = url,
                    timestamp = System.currentTimeMillis(),
                    mode = TrafficConfigProfileRefreshMode.Automatic,
                )
            }
            application.stateStore.reconcileTrafficConfigProxyGroups()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
