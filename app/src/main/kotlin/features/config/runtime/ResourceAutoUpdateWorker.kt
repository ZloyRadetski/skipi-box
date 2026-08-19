// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import features.resources.resourceFileUpdateOptions
import features.resources.resourceFileUpdateSource
import features.resources.runtime.AndroidResourceFileRepository

internal class ResourceAutoUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val configId = inputData.getInt(TrafficConfigIdKey, -1)
        if (configId == -1) return Result.success()
        val application = applicationContext as? SkipiApplication ?: return Result.failure()
        val state = application.stateStore.state.value
        val config = state.trafficConfigs.firstOrNull { it.id == configId }
            ?: return Result.success()
        if (!config.resourceSettings.autoUpdate) {
            return Result.success()
        }
        return runCatching {
            val resourceSettings = config.resourceSettings
            val options = state.resourceFileUpdateOptions(resourceSettings.userAgent)
            val source = resourceSettings.resourceFileUpdateSource()
            val repository = AndroidResourceFileRepository(application.applicationContext)
            repository.update(
                source = source,
                options = options,
                customResourceFiles = resourceSettings.customFiles,
            )
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
