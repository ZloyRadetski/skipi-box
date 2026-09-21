// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import features.subscription.SubscriptionSchedule
import features.subscription.parseSubscriptionSchedule

/**
 * Platform-neutral fields required to schedule one subscription refresh.
 * Platform state objects adapt to this type at their boundary.
 */
data class SubscriptionRefreshTarget(
    val id: Int,
    val url: String,
    val enabled: Boolean,
    val updateInterval: String,
    val lastUpdatedAtMillis: Long = 0L,
)

/** A pure decision for timer-driven subscription refresh implementations. */
data class SubscriptionRefreshPlan(
    val dueSubscriptionIds: List<Int>,
    val nextWakeupAtMillis: Long?,
    val nextDelayMillis: Long?,
)

/**
 * Computes due subscriptions and the earliest next wake-up without I/O,
 * networking, persistence, or clock access.
 */
fun planSubscriptionRefreshes(
    targets: List<SubscriptionRefreshTarget>,
    nowMillis: Long,
): SubscriptionRefreshPlan {
    val dueSubscriptionIds = mutableListOf<Int>()
    var nextWakeupAtMillis: Long? = null

    targets.forEach { target ->
        val intervalMillis = target.refreshIntervalMillisOrNull() ?: return@forEach
        val refreshAtMillis = target.nextRefreshAtMillis(intervalMillis)
        val wakeupAtMillis = refreshAtMillis.coerceAtLeast(nowMillis)

        if (refreshAtMillis <= nowMillis) {
            dueSubscriptionIds += target.id
        }
        val currentNextWakeupAtMillis = nextWakeupAtMillis
        if (currentNextWakeupAtMillis == null || wakeupAtMillis < currentNextWakeupAtMillis) {
            nextWakeupAtMillis = wakeupAtMillis
        }
    }

    return SubscriptionRefreshPlan(
        dueSubscriptionIds = dueSubscriptionIds,
        nextWakeupAtMillis = nextWakeupAtMillis,
        nextDelayMillis = nextWakeupAtMillis?.let { wakeup -> wakeup.delayFrom(nowMillis) },
    )
}

enum class SubscriptionExistingWorkPolicy {
    UPDATE,
}

data class SubscriptionWorkSpec(
    val groupId: Int,
    val uniqueName: String,
    val repeatIntervalMillis: Long,
    val requiresConnectedNetwork: Boolean,
    val policy: SubscriptionExistingWorkPolicy,
    val backoffMillis: Long,
)

/** Platform gateway for persistent periodic subscription work. */
interface SubscriptionScheduleGateway {
    fun scheduledGroupIds(): Set<Int>

    fun enqueue(spec: SubscriptionWorkSpec)

    fun cancel(groupId: Int)

    fun storeScheduledGroupIds(groupIds: Set<Int>)
}

/**
 * Reconciles persistent periodic work with the current subscription targets.
 * Android uses WorkManager behind [SubscriptionScheduleGateway]; other
 * platforms may use their own scheduler without duplicating the eligibility
 * rules.
 */
class SubscriptionScheduler(
    private val gateway: SubscriptionScheduleGateway,
) {
    fun reconcile(targets: List<SubscriptionRefreshTarget>) {
        val desired = targets.mapNotNull { target ->
            val schedule = target.refreshIntervalMillisOrNull() ?: return@mapNotNull null
            SubscriptionWorkSpec(
                groupId = target.id,
                uniqueName = subscriptionWorkName(target.id),
                repeatIntervalMillis = schedule,
                requiresConnectedNetwork = true,
                policy = SubscriptionExistingWorkPolicy.UPDATE,
                backoffMillis = MinimumSubscriptionBackoffMillis,
            )
        }
        val desiredIds = desired.mapTo(mutableSetOf()) { it.groupId }
        (gateway.scheduledGroupIds() - desiredIds).forEach(gateway::cancel)
        desired.forEach(gateway::enqueue)
        gateway.storeScheduledGroupIds(desiredIds)
    }
}

fun subscriptionWorkName(groupId: Int): String = "subscription-update-$groupId"

private fun SubscriptionRefreshTarget.refreshIntervalMillisOrNull(): Long? {
    if (!enabled || url.isBlank()) return null
    return (parseSubscriptionSchedule(updateInterval) as? SubscriptionSchedule.Enabled)
        ?.repeatIntervalMillis
}

private fun SubscriptionRefreshTarget.nextRefreshAtMillis(intervalMillis: Long): Long {
    if (lastUpdatedAtMillis <= 0L) return Long.MIN_VALUE
    return if (lastUpdatedAtMillis > Long.MAX_VALUE - intervalMillis) {
        Long.MAX_VALUE
    } else {
        lastUpdatedAtMillis + intervalMillis
    }
}

private fun Long.delayFrom(nowMillis: Long): Long = when {
    this <= nowMillis -> 0L
    nowMillis < 0L && this > Long.MAX_VALUE + nowMillis -> Long.MAX_VALUE
    else -> this - nowMillis
}

private const val MinimumSubscriptionBackoffMillis = 15 * 60 * 1_000L
