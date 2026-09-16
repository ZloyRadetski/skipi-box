// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.os.SystemClock

/**
 * Lets a full VPN operation (server switch or disconnect) take ownership of a
 * network handover before the service starts a smaller TUN/core recovery.
 *
 * Callers complete operation-owned reservations in a finally block, so an
 * in-progress full restart cannot be pre-empted by a second recovery.
 */
internal class NetworkHandoverRecoveryDeferral(
    private val nowMillis: () -> Long,
    private val maxDeferralMillis: Long?,
) {
    private val lock = Any()
    private var nextToken = 0L
    private var pendingToken: Long? = null
    private var deadlineMillis = 0L

    fun begin(): Long = synchronized(lock) {
        (++nextToken).also { token ->
            pendingToken = token
            deadlineMillis = maxDeferralMillis?.let { maxMillis -> nowMillis() + maxMillis } ?: Long.MAX_VALUE
        }
    }

    fun isPending(): Boolean = synchronized(lock) {
        expireIfNeeded()
        pendingToken != null
    }

    fun complete(token: Long) {
        synchronized(lock) {
            if (pendingToken == token) {
                pendingToken = null
                deadlineMillis = 0L
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            pendingToken = null
            deadlineMillis = 0L
        }
    }

    private fun expireIfNeeded() {
        if (maxDeferralMillis != null && pendingToken != null && nowMillis() >= deadlineMillis) {
            pendingToken = null
            deadlineMillis = 0L
        }
    }
}

internal object NetworkHandoverRecoveryGate {
    private val externalVpnOperationDeferral = NetworkHandoverRecoveryDeferral(
        nowMillis = SystemClock::elapsedRealtime,
        maxDeferralMillis = null,
    )
    private val networkAutomationEvaluationDeferral = NetworkHandoverRecoveryDeferral(
        nowMillis = SystemClock::elapsedRealtime,
        maxDeferralMillis = MaxNetworkAutomationEvaluationDeferralMillis,
    )

    fun beginExternalVpnOperation(): Long = externalVpnOperationDeferral.begin()

    fun completeExternalVpnOperation(token: Long) = externalVpnOperationDeferral.complete(token)

    /** Reserves a handover while network-rule evaluation is being debounced. */
    fun beginNetworkAutomationEvaluation(): Long = networkAutomationEvaluationDeferral.begin()

    fun completeNetworkAutomationEvaluation(token: Long) = networkAutomationEvaluationDeferral.complete(token)

    fun clearNetworkAutomationEvaluation() = networkAutomationEvaluationDeferral.clear()

    fun isNetworkAutomationHandoverPending(): Boolean {
        return externalVpnOperationDeferral.isPending() || networkAutomationEvaluationDeferral.isPending()
    }

    fun clear() {
        externalVpnOperationDeferral.clear()
        networkAutomationEvaluationDeferral.clear()
    }

    private const val MaxNetworkAutomationEvaluationDeferralMillis = 5_000L
}
