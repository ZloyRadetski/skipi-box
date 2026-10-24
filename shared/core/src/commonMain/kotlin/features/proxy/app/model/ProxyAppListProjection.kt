// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app.model

data class AppPackageEntry(
    val packageName: String,
    val appLabel: String? = null,
    val userId: Int? = null,
    val uid: Int? = null,
    val system: Boolean = false,
)

val AppPackageEntry.name: String
    get() = appLabel?.ifBlank { packageName } ?: packageName

data class ProxyAppListUserSpace(
    val id: Int,
    val name: String,
)

data class ProxyAppListUserSpaceTabUi(
    val id: Int,
    val label: String,
    val checkedCount: Int,
)

data class ProxyAppListItem(
    val key: String,
    val app: AppPackageEntry,
    val sharedUid: Boolean,
    val selectionKeys: List<String>,
)

data class ProxyAppListPreparedData(
    val displayedUserSpaces: List<ProxyAppListUserSpace> = emptyList(),
    val allItemsByUser: Map<Int, List<ProxyAppListItem>> = emptyMap(),
    val visibleItemsByUser: Map<Int, List<ProxyAppListItem>> = emptyMap(),
) {
    companion object {
        val Empty = ProxyAppListPreparedData()
    }
}

fun prepareProxyAppListProjection(
    userSpaces: List<ProxyAppListUserSpace>,
    appPackages: List<AppPackageEntry>,
    searchValue: String,
    fixedUserId: Int?,
): ProxyAppListPreparedData {
    val appsByUser = appPackages.groupByUserSpace(fixedUserId = fixedUserId)
    val displayedUserSpaces = if (fixedUserId == null) {
        userSpaces.withAppPackageUserSpaces(appsByUser)
    } else {
        userSpaces
    }
    val keyword = searchValue.trim()
    val allItemsByUser = LinkedHashMap<Int, List<ProxyAppListItem>>(appsByUser.size)
    val visibleItemsByUser = if (keyword.isEmpty()) {
        allItemsByUser
    } else {
        LinkedHashMap(appsByUser.size)
    }

    appsByUser.forEach { (userId, apps) ->
        val selectionInfo = apps.selectionInfo()
        val allItems = apps.toListItems(
            searchValue = "",
            selectionInfo = selectionInfo,
        )
        allItemsByUser[userId] = allItems
        if (keyword.isNotEmpty()) {
            visibleItemsByUser[userId] = apps.toListItems(
                searchValue = keyword,
                selectionInfo = selectionInfo,
            )
        }
    }

    return ProxyAppListPreparedData(
        displayedUserSpaces = displayedUserSpaces,
        allItemsByUser = allItemsByUser,
        visibleItemsByUser = visibleItemsByUser,
    )
}

fun ProxyAppListPreparedData.projectedUserSpaceTabs(
    selectedAppKeys: Set<String>,
): List<ProxyAppListUserSpaceTabUi> = displayedUserSpaces.map { user ->
    val userItems = allItemsByUser[user.id].orEmpty()
    user.toUserSpaceTab(
        checkedCount = userItems.count { item ->
            item.selectionKeys.any { key -> key in selectedAppKeys }
        },
    )
}

fun List<AppPackageEntry>.sortedForProxyAppListRefresh(
    selectedAppKeys: Set<String>,
): List<AppPackageEntry> {
    val selectionInfo = selectionInfo()
    return sortedWith(
        compareByDescending<AppPackageEntry> { app ->
            app.selectionKeys(selectionInfo).any { key -> key in selectedAppKeys }
        }
            .thenBy { app -> app.groupSortName(selectionInfo) }
            .thenBy { app -> app.groupSortKey(selectionInfo) }
            .thenBy { app -> app.name.normalizedSortText() }
            .thenBy { app -> app.packageName },
    )
}

private fun List<AppPackageEntry>.groupByUserSpace(
    fixedUserId: Int? = null,
): Map<Int, List<AppPackageEntry>> {
    if (fixedUserId == null) {
        return groupBy { entry -> entry.userId ?: 0 }
    }
    return mapOf(
        fixedUserId to map { entry ->
            if (entry.userId == fixedUserId) {
                entry
            } else {
                entry.copy(
                    userId = fixedUserId,
                    uid = entry.uid?.toAndroidAppId(),
                )
            }
        }.distinctBy { entry -> entry.packageName },
    )
}

private fun List<ProxyAppListUserSpace>.withAppPackageUserSpaces(
    appPackagesByUser: Map<Int, List<AppPackageEntry>>,
): List<ProxyAppListUserSpace> {
    val knownUserIds = map { user -> user.id }.toSet()
    val fallbackUsers = appPackagesByUser.keys
        .filterNot { userId -> userId in knownUserIds }
        .sorted()
        .map { userId -> ProxyAppListUserSpace(id = userId, name = "User $userId") }
    return (this + fallbackUsers)
        .distinctBy { user -> user.id }
        .sortedBy { user -> user.id }
}

private fun List<AppPackageEntry>.toListItems(
    searchValue: String,
    selectionInfo: AppSelectionInfo,
): List<ProxyAppListItem> {
    val keyword = searchValue.trim()
    return asSequence()
        .filter { app ->
            keyword.isEmpty() ||
                app.name.contains(keyword, ignoreCase = true) ||
                app.packageName.contains(keyword, ignoreCase = true)
        }
        .map { app ->
            val selectionKeys = app.selectionKeys(selectionInfo)
            ProxyAppListItem(
                key = app.selectionKey(),
                app = app,
                sharedUid = app.uid in selectionInfo.sharedUids,
                selectionKeys = selectionKeys,
            )
        }
        .distinctBy { item -> item.key }
        .toList()
}

private fun ProxyAppListUserSpace.toUserSpaceTab(
    checkedCount: Int,
): ProxyAppListUserSpaceTabUi = ProxyAppListUserSpaceTabUi(
    id = id,
    label = name.ifBlank { "User $id" },
    checkedCount = checkedCount,
)

private data class AppSelectionInfo(
    val keysByUid: Map<Int, List<String>>,
    val sharedUids: Set<Int>,
    val sortNamesByUid: Map<Int, String>,
)

private fun List<AppPackageEntry>.selectionInfo(): AppSelectionInfo {
    val appsByUid = filter { app -> app.uid != null }
        .groupBy { app -> app.uid ?: 0 }
    return AppSelectionInfo(
        keysByUid = appsByUid.mapValues { (_, apps) ->
            apps.map { app -> app.selectionKey() }.distinct()
        },
        sharedUids = appsByUid
            .filterValues { apps -> apps.size > 1 }
            .keys,
        sortNamesByUid = appsByUid.mapValues { (_, apps) ->
            apps.minOf { app -> app.name.normalizedSortText() }
        },
    )
}

private fun AppPackageEntry.selectionKeys(
    selectionInfo: AppSelectionInfo,
): List<String> {
    val currentUid = uid ?: return listOf(selectionKey())
    return selectionInfo.keysByUid[currentUid] ?: listOf(selectionKey())
}

private fun AppPackageEntry.selectionKey(): String = "${userId ?: 0}:$packageName"

private fun AppPackageEntry.groupSortName(
    selectionInfo: AppSelectionInfo,
): String {
    val currentUid = uid ?: return name.normalizedSortText()
    return selectionInfo.sortNamesByUid[currentUid] ?: name.normalizedSortText()
}

private fun AppPackageEntry.groupSortKey(
    selectionInfo: AppSelectionInfo,
): String {
    val currentUid = uid
    return if (currentUid != null && currentUid in selectionInfo.sharedUids) {
        "uid:$currentUid"
    } else {
        "app:${selectionKey()}"
    }
}

private const val AndroidUserIdRange = 100_000

private fun Int.toAndroidAppId(): Int = this % AndroidUserIdRange

private fun String.normalizedSortText(): String = lowercase()
