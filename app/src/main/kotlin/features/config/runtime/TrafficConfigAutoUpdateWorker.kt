// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.config.withConfigProxyGroupsReflected
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import features.config.withUpdatedTrafficConfig
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
            val fetched = application.subscriptionFetcher.fetch(
                url = url,
                userAgent = normalizeSkipiUserAgent(config.resourceSettings.userAgent),
                options = application.stateStore.state.value.toSubscriptionFetchOptions(),
            )
            val normalized = fetched.trimEnd() + "\n"
            val analysis = normalized.analyzeShadowrocketConfig()
            if (analysis.diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
                return@runCatching Result.retry()
            }
            application.stateStore.update { state ->
                state.withUpdatedTrafficConfig(config.id) { current ->
                    current.copy(
                        rawConfig = normalized,
                        sourceUrl = url,
                        lastUpdatedAtMillis = System.currentTimeMillis(),
                    ).withSkipiSettingsReadFromRawConfig().let { parsed ->
                        parsed.copy(
                            sourceUrl = url.ifBlank { parsed.sourceUrl },
                            updateLocked = current.updateLocked,
                            autoUpdate = current.autoUpdate,
                            updateInterval = current.updateInterval,
                            resourceSettings = current.resourceSettings.copy(
                                source = parsed.resourceSettings.source,
                                customGeoIpUrl = parsed.resourceSettings.customGeoIpUrl,
                                customGeoSiteUrl = parsed.resourceSettings.customGeoSiteUrl,
                                customGeoIpOnlyCnPrivateUrl = parsed.resourceSettings.customGeoIpOnlyCnPrivateUrl,
                                customDirectCidrIpv4Url = parsed.resourceSettings.customDirectCidrIpv4Url,
                                customDirectCidrIpv6Url = parsed.resourceSettings.customDirectCidrIpv6Url,
                                customFiles = parsed.resourceSettings.customFiles,
                                nextCustomFileId = parsed.resourceSettings.nextCustomFileId,
                                userAgent = parsed.resourceSettings.userAgent,
                                autoUpdate = current.resourceSettings.autoUpdate,
                                updateInterval = current.resourceSettings.updateInterval,
                            ),
                        ).withSkipiSettingsInRawConfig()
                    }
                }.withConfigProxyGroupsReflected()
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
