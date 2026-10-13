// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import features.proxy.server.model.HTTP
import features.proxy.server.usecase.ProxyServerListSubscriptionFailure
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdate
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdateResult
import features.proxy.server.usecase.SubscriptionGroupFetchIdentity
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Test
import kotlin.test.assertEquals

class SubscriptionUpdateMessageTest {
    @Test
    fun successfulFetchesUseLoadedGroupAndServerCounts() {
        val message = subscriptionUpdateMessage(
            result = result(
                updates = listOf(update(groupId = 1, serverCount = 2), update(groupId = 2, serverCount = 1)),
            ),
            successTemplate = "ok {groupCount}/{serverCount}",
            failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
        )

        assertEquals("ok 2/3", message)
    }

    @Test
    fun partialFetchFailureUsesTemplateCountsAndTheFirstFormattedFailure() {
        val message = subscriptionUpdateMessage(
            result = result(
                updates = listOf(update(groupId = 1, serverCount = 2)),
                failures = listOf(
                    ProxyServerListSubscriptionFailure(2, SocketTimeoutException("slow")),
                    ProxyServerListSubscriptionFailure(3, IllegalStateException("second failure")),
                ),
            ),
            successTemplate = "ok {groupCount}/{serverCount}",
            failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
        )

        assertEquals("partial 1/2/2: Timeout connecting to server", message)
    }

    @Test
    fun allFetchFailuresShowOnlyTheFirstFormattedFailure() {
        val message = subscriptionUpdateMessage(
            result = result(
                failures = listOf(
                    ProxyServerListSubscriptionFailure(1, UnknownHostException("provider.example")),
                    ProxyServerListSubscriptionFailure(2, IllegalStateException("second failure")),
                ),
            ),
            successTemplate = "ok {groupCount}/{serverCount}",
            failedTemplate = "partial {groupCount}/{failedCount}/{serverCount}",
        )

        assertEquals("DNS error: provider.example", message)
    }

    private fun result(
        updates: List<ProxyServerListSubscriptionUpdate> = emptyList(),
        failures: List<ProxyServerListSubscriptionFailure> = emptyList(),
    ) = ProxyServerListSubscriptionUpdateResult(
        updates = updates,
        failures = failures,
        updatedAtMillis = 1L,
    )

    private fun update(groupId: Int, serverCount: Int) = ProxyServerListSubscriptionUpdate(
        groupId = groupId,
        sourceIdentity = SubscriptionGroupFetchIdentity(
            url = "https://subscription-$groupId.example/update",
            userAgent = "",
            updateInterval = "",
            ageSecretKey = "",
            updateViaProxy = false,
            enabled = true,
        ),
        urlCount = 1,
        servers = List(serverCount) { index -> HTTP(server = "proxy-$groupId-$index.example") },
    )
}
