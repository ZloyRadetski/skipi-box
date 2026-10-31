// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class ConnectedLatencyStep {
    /** Measure the delay of the connection that is up right now. */
    TestConnection,

    /** Measure every server of the selected server's group or subscription. */
    PingGroup,
}

/**
 * The compact ping button while connected: the first tap measures the live connection, a second
 * tap within [window] measures the whole group, and the tap after that starts over.
 */
class ConnectedLatencyTapPolicy(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val window: Duration = 5.seconds,
) {
    private var lastConnectionTest: TimeMark? = null

    fun nextStep(): ConnectedLatencyStep {
        val last = lastConnectionTest
        if (last != null && last.elapsedNow() < window) {
            lastConnectionTest = null
            return ConnectedLatencyStep.PingGroup
        }
        lastConnectionTest = timeSource.markNow()
        return ConnectedLatencyStep.TestConnection
    }
}

/**
 * The action behind the repeated ping tap: the subscription of the selected server is pinged as a
 * whole, any other group is tested server by server. Null when the selected server has no group.
 */
fun ProxyHomeUiState.connectedLatencyGroupAction(): ProxyHomeAction? {
    val server = selectedServer ?: return null
    val group = groups.firstOrNull { it.id == server.groupId }
        ?: groups.firstOrNull { it.serverIds?.contains(server.id) == true }
        ?: return null
    val subscription = group.subscription
    return if (subscription != null) {
        ProxyHomeAction.PingSubscription(subscription.id)
    } else {
        ProxyHomeAction.TestGroup(group.id)
    }
}
