// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.repository

import app.skipi.app.model.ProxyServerRecord
import features.proxy.server.model.HTTP
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProxyServerRepositoryContractTest {
    @Test
    fun selectingExistingServerDelegatesToRepository() = runTest {
        val repository = FakeProxyServerRepository(
            listOf(ProxyServerRecord(id = 42, server = HTTP(server = "proxy.example"))),
        )

        repository.selectExisting(42)

        assertEquals(42, repository.selectedServerId)
    }

    @Test
    fun selectionRejectsIdsOutsideCurrentCatalog() = runTest {
        val repository = FakeProxyServerRepository(emptyList())

        val failure = try {
            repository.selectExisting(42)
            null
        } catch (error: IllegalArgumentException) {
            error
        }

        assertNotNull(failure)
        assertEquals(null, repository.selectedServerId)
    }

    private class FakeProxyServerRepository(records: List<ProxyServerRecord>) : ProxyServerRepository {
        override val servers = MutableStateFlow(records)
        var selectedServerId: Int? = null
            private set

        override suspend fun select(serverId: Int) {
            selectedServerId = serverId
        }

        override suspend fun upsert(server: ProxyServerRecord) = Unit

        override suspend fun remove(serverId: Int) = Unit
    }
}
