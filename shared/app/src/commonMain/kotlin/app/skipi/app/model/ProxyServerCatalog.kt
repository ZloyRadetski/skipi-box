// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

/** Complete persisted proxy catalogue metadata used for one atomic shared update. */
data class ProxyServerCatalog(
    val servers: List<ProxyServerRecord> = emptyList(),
    val nextServerId: Int = 1,
    val selectedServerId: Int = 1,
    /**
     * IDs of persisted rows the adapter keeps opaque. Shared operations use them for selection and
     * allocation only; the adapter remains responsible for each row and its payload.
     */
    val retainedServerIds: Set<Int> = emptySet(),
)
