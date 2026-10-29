// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import features.proxy.app.model.ProxyAppListUserSpace
import system.user.AndroidUserSpaceRepository

class AndroidUserSpaceProvider(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val userSpaceRepository = AndroidUserSpaceRepository(appContext)

    suspend fun getCurrentAndroidUser(): AndroidUser = withContext(Dispatchers.Default) {
        appContext.currentAndroidUser()
    }

    suspend fun listAndroidUsers(): List<AndroidUser> {
        return listOf(getCurrentAndroidUser())
    }

    suspend fun currentAndroidUserSpace(): ProxyAppListUserSpace {
        return userSpaceRepository.currentAndroidUserSpace()
    }

    suspend fun listAndroidUserSpaces(): List<ProxyAppListUserSpace> {
        return userSpaceRepository.listAndroidUserSpaces()
    }
}
