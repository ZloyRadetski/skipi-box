// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import data.AndroidAppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SubscriptionExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val stateStore = AndroidAppStateStore.get(appContext)
                val state = stateStore.state.value
                SubscriptionExpiryNotifier.checkAndNotify(appContext, state)
                SubscriptionExpiryScheduler.schedule(appContext, state)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
