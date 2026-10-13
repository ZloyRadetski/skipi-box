// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord
import features.proxy.server.model.HTTP
import features.proxy.server.model.VLESS
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubscriptionRefreshBatchCancellationTest {
    @Test
    fun cancellationAfterCatalogWriteDoesNotSkipAndroidMetadataWriteBoundary() = runTest {
        val first = cancellationSubscription(4)
        val second = cancellationSubscription(5)
        lateinit var callerJob: Job
        val port = CancellingBatchCommitPort(
            latest = SubscriptionRefreshSnapshot(
                subscriptions = listOf(first, second),
                servers = listOf(
                    ProxyServerRecord(7, first.id, HTTP(remarks = "Old first", server = "old-first.example.test", port = "443")),
                    ProxyServerRecord(8, second.id, HTTP(remarks = "Old second", server = "old-second.example.test", port = "443")),
                ),
                nextServerId = 9,
                selectedServerId = 7,
            ),
            afterCatalogWrite = {
                callerJob.cancel(CancellationException("caller cancelled after catalog persistence"))
            },
        )

        val apply = async {
            callerJob = coroutineContext[Job]!!
            commitSubscriptionRefreshBatch(
                loaded = listOf(
                    cancellationLoaded(first, "First refreshed"),
                    cancellationLoaded(second, "Second refreshed"),
                ),
                refreshedAtMillis = 1_400L,
                singlePort = unusedCancellationSingleCommitPort(),
                batchPort = port,
            )
        }

        assertFailsWith<CancellationException> { apply.await() }
        assertEquals(listOf("catalog", "metadata-and-profiles"), port.events)
    }
}

private class CancellingBatchCommitPort(
    var latest: SubscriptionRefreshSnapshot,
    private val afterCatalogWrite: () -> Unit,
) : SubscriptionRefreshBatchCommitPort {
    val events = mutableListOf<String>()

    override suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult {
        events += "catalog"
        val result = reconcileLatest(latest)
        if (result.applicable) {
            latest = latest.copy(
                servers = result.snapshot.servers,
                nextServerId = result.snapshot.nextServerId,
                selectedServerId = result.snapshot.selectedServerId,
            )
        }
        afterCatalogWrite()
        return result
    }

    override suspend fun updateMetadataAndProfiles(
        applyLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult {
        events += "metadata-and-profiles"
        val result = applyLatest(latest)
        if (result.applicable) latest = latest.copy(subscriptions = result.snapshot.subscriptions)
        return result
    }
}

private fun cancellationSubscription(id: Int) = SubscriptionRecord(
    id = id,
    title = "Stored $id",
    url = "https://subscription.example.test/$id",
    userAgent = "Skipi/1 Android",
    updateInterval = "6",
    hwid = "device-$id",
    ageSecretKey = "",
    updateViaProxy = false,
    autoOverrideRules = false,
    enabled = true,
    builtIn = false,
    metadata = SubscriptionMetadata(profileDescription = "Stored description $id"),
    lastUpdatedAtMillis = 100L,
    notifyOnExpiry = false,
    customExpiryReminders = null,
)

private fun cancellationLoaded(subscription: SubscriptionRecord, remarks: String) = LoadedSubscriptionRefresh(
    sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
    urlCount = 1,
    servers = listOf(
        VLESS(
            remarks = remarks,
            id = "uuid-$remarks",
            server = "${subscription.id}.new.example.test",
            port = "443",
        ),
    ),
    metadata = SubscriptionMetadata(profileTitle = "$remarks metadata"),
    resolvedEmbeddedConfig = null,
)

private fun unusedCancellationSingleCommitPort() = object : SubscriptionRefreshCommitPort {
    override suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshReconciliationResult,
    ): SubscriptionRefreshReconciliationResult = error("batch path must not call single catalog port")

    override suspend fun updateSubscription(
        subscriptionId: Int,
        transform: (SubscriptionRecord?) -> SubscriptionRecord?,
    ): SubscriptionRecord? = error("batch path must not call single metadata port")

    override suspend fun applyEmbeddedConfig(config: ResolvedEmbeddedSubscriptionConfig): Boolean =
        error("batch path must not call single embedded-profile port")
}
