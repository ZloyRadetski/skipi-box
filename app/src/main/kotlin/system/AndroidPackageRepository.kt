// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import features.logs.AndroidAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidPackageRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun listPackages(type: String): List<String> {
        return getCurrentUserPackagesInfo(type)
            .map { entry -> entry.packageName }
            .distinct()
            .sorted()
    }

    suspend fun getPackagesInfo(packages: List<String>): List<InstalledPackageInfo> = withContext(Dispatchers.IO) {
        val packageSet = packages.toSet()
        if (packageSet.isEmpty()) {
            return@withContext emptyList()
        }

        val packageManager = appContext.packageManager
        val packageEntries = getCurrentUserPackagesInfo(ANDROID_PACKAGE_SCOPE_ALL)
            .filter { entry -> entry.packageName in packageSet }
        val applicationInfoCache = HashMap<String, ApplicationInfo?>()

        packageEntries.map { entry ->
            val applicationInfo = applicationInfoCache.getOrPut(entry.packageName) {
                runCatching { packageManager.getApplicationInfoCompat(entry.packageName) }
                    .onFailure { error ->
                        AndroidAppLogger.warn(LogTag, "Failed to load application info for ${entry.packageName}", error)
                    }
                    .getOrNull()
            }
            InstalledPackageInfo(
                packageName = entry.packageName,
                appLabel = applicationInfo?.loadLabel(packageManager)?.toString() ?: entry.packageName,
                isSystem = applicationInfo?.isSystemApp() == true,
                uid = entry.uid,
            )
        }.sortedByAppIdentity()
    }

    suspend fun getCurrentUserPackagesInfo(type: String): List<InstalledPackageInfo> = withContext(Dispatchers.IO) {
        val packageManager = appContext.packageManager
        packageManager.getInstalledApplicationsCompat()
            .mapNotNull { applicationInfo ->
                val matchesPackageScope = when (type) {
                    ANDROID_PACKAGE_SCOPE_SYSTEM -> applicationInfo.isSystemApp()
                    ANDROID_PACKAGE_SCOPE_USER -> !applicationInfo.isSystemApp()
                    else -> true
                }
                if (!matchesPackageScope) {
                    return@mapNotNull null
                }
                InstalledPackageInfo(
                    packageName = applicationInfo.packageName,
                    appLabel = applicationInfo.loadLabel(packageManager).toString(),
                    isSystem = applicationInfo.isSystemApp(),
                    uid = applicationInfo.uid,
                )
            }
            .distinctBy { info -> "${info.uid}:${info.packageName}" }
            .sortedByAppIdentity()
    }

    private companion object {
        private const val LogTag = "AndroidPackageRepository"
    }
}

internal fun Context.currentAndroidUser(): AndroidUser {
    val userId = Process.myUid().toAndroidUserId()
    return AndroidUser(
        id = userId,
        name = "User $userId",
    )
}

private fun ApplicationInfo.isSystemApp(): Boolean {
    return flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}

private fun List<InstalledPackageInfo>.sortedByAppIdentity(): List<InstalledPackageInfo> {
    return sortedWith(
        compareBy<InstalledPackageInfo> { info -> info.uid }
            .thenBy { info -> info.appLabel },
    )
}
