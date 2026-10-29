// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app.model

import androidx.compose.runtime.Immutable
import system.ANDROID_PACKAGE_SCOPE_ALL
import system.ANDROID_PACKAGE_SCOPE_USER
import system.toAndroidUserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import system.AndroidPackageProvider
import system.AndroidUser
import system.InstalledPackageInfo
import features.proxy.app.model.ProxyAppListUserSpace
import utils.toTrimmedNonEmptyDistinctList

@Immutable
data class ProxyAppIconRequest(
    val packageName: String,
    val sizePx: Int,
)

internal suspend fun AndroidPackageProvider.loadProxyAppListPackages(
    showSystemApps: Boolean,
    currentUserOnly: Boolean,
    excludedPackageName: String,
): List<AppPackageEntry> = withContext(Dispatchers.Default) {
    val scope = if (showSystemApps) ANDROID_PACKAGE_SCOPE_ALL else ANDROID_PACKAGE_SCOPE_USER
    if (currentUserOnly) {
        return@withContext getCurrentUserPackagesInfo(scope)
            .map(InstalledPackageInfo::toAppPackageEntry)
            .filterNot { entry -> entry.packageName == excludedPackageName }
            .distinctBy { entry -> "${entry.userId}:${entry.uid}:${entry.packageName}" }
            .sortedWith(
                compareBy<AppPackageEntry> { entry -> entry.userId ?: 0 }
                    .thenBy { entry -> entry.name },
            )
    }

    val packageNames = listPackages(scope)
        .normalizedPackageNames()

    if (packageNames.isEmpty()) {
        return@withContext emptyList()
    }

    getPackagesInfo(packageNames)
        .map(InstalledPackageInfo::toAppPackageEntry)
        .filterNot { entry -> entry.packageName == excludedPackageName }
        .distinctBy { entry -> "${entry.userId}:${entry.uid}:${entry.packageName}" }
        .sortedWith(
            compareBy<AppPackageEntry> { entry -> entry.userId ?: 0 }
                .thenBy { entry -> entry.name },
        )
}

internal fun AndroidUser.toAndroidUserSpace(): ProxyAppListUserSpace {
    return ProxyAppListUserSpace(id = id, name = name)
}

internal suspend fun prepareProxyAppListData(
    userSpaces: List<ProxyAppListUserSpace>,
    appPackages: List<AppPackageEntry>,
    searchValue: String,
    fixedUserId: Int?,
): ProxyAppListPreparedData = withContext(Dispatchers.Default) {
    prepareProxyAppListProjection(
        userSpaces = userSpaces,
        appPackages = appPackages,
        searchValue = searchValue,
        fixedUserId = fixedUserId,
    )
}

internal suspend fun ProxyAppListPreparedData.toUserSpaceTabs(
    selectedAppKeys: Set<String>,
): List<ProxyAppListUserSpaceTabUi> = withContext(Dispatchers.Default) {
    projectedUserSpaceTabs(selectedAppKeys)
}

private fun List<String>.normalizedPackageNames(): List<String> =
    toTrimmedNonEmptyDistinctList().sorted()

private fun InstalledPackageInfo.toAppPackageEntry(
): AppPackageEntry {
    return AppPackageEntry(
        packageName = packageName,
        appLabel = appLabel.ifBlank { packageName },
        userId = uid.toAndroidUserId(),
        uid = uid,
        system = isSystem,
    )
}
