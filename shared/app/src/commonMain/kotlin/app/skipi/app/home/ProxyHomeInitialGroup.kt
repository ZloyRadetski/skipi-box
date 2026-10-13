// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/**
 * Seeds Home from the persisted selection when its decoded server record is available.
 * Hosts keep their own server and group ID types, aliases, and fallback values.
 */
fun <ServerId, GroupId> rawInitialProxyHomeGroupId(
    selectedServerId: ServerId?,
    decodedServerGroupIds: List<Pair<ServerId, GroupId>>,
    defaultGroupId: GroupId,
): GroupId = selectedServerId
    ?.let { selectedId -> decodedServerGroupIds.firstOrNull { (serverId, _) -> serverId == selectedId }?.second }
    ?: defaultGroupId
