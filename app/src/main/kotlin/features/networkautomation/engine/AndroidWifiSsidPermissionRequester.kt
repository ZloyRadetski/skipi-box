// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

import android.Manifest
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bridges the explicit "use current Wi-Fi" action to MainActivity's permission launcher. */
class AndroidWifiSsidPermissionRequester(
    private val hasFineLocationPermission: () -> Boolean,
    private val missingLauncherMessage: () -> String,
) {
    private val mutex = Mutex()
    private var launcher: ((Array<String>) -> Unit)? = null
    private var pendingResult: CompletableDeferred<Boolean>? = null

    fun registerLauncher(launcher: ((Array<String>) -> Unit)?) {
        this.launcher = launcher
    }

    fun complete(granted: Boolean) {
        pendingResult?.complete(granted)
        pendingResult = null
    }

    suspend fun request(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasFineLocationPermission()) return true
        return mutex.withLock {
            if (hasFineLocationPermission()) return@withLock true
            val activeLauncher = launcher ?: error(missingLauncherMessage())
            val result = CompletableDeferred<Boolean>()
            pendingResult = result
            try {
                withContext(Dispatchers.Main.immediate) {
                    // Android 12+ requires requesting coarse and fine together for a precise choice.
                    activeLauncher(arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ))
                }
                result.await()
            } finally {
                if (pendingResult === result) pendingResult = null
            }
        }
    }
}
