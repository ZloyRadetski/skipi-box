// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system.user

import android.content.Context
import android.os.Process
import android.os.UserManager
import features.logs.AppLog
import system.toAndroidUserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import features.proxy.app.model.ProxyAppListUserSpace

class AndroidUserSpaceRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun currentAndroidUserSpace(): ProxyAppListUserSpace = withContext(Dispatchers.Default) {
        appContext.currentAndroidUserSpace()
    }

    suspend fun listAndroidUserSpaces(): List<ProxyAppListUserSpace> = withContext(Dispatchers.Default) {
        val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
        val currentUserHandle = Process.myUserHandle()
        val currentUserSpace = appContext.currentAndroidUserSpace()
        val profiles = runCatching { userManager.userProfiles }
            .onFailure { error -> AppLog.warn(LogTag, "Failed to list Android user profiles", error) }
            .getOrDefault(listOf(currentUserHandle))

        profiles
            .map { userHandle ->
                val userId = userHandle.hashCode()
                ProxyAppListUserSpace(
                    id = userId,
                    name = if (userId == currentUserSpace.id) {
                        currentUserSpace.name
                    } else {
                        "Profile $userId"
                    },
                )
            }
            .distinctBy { user -> user.id }
            .sortedBy { user -> user.id }
            .ifEmpty { listOf(currentUserSpace) }
    }

    private fun Context.currentAndroidUserSpace(): ProxyAppListUserSpace {
        val userId = Process.myUid().toAndroidUserId()
        return ProxyAppListUserSpace(
            id = userId,
            name = "User $userId",
        )
    }

    private companion object {
        private const val LogTag = "AndroidUserSpaceRepository"
    }
}
