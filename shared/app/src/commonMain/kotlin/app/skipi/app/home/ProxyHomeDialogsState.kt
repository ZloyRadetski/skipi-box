// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/** QR presentation payload shared by Home hosts. */
data class ProxyHomeQrPayload(
    val title: String,
    val text: String,
)

/**
 * Pending Home dialog presentation data. Payload types stay owned by each host,
 * while the state shape and which editor fields survive recreation stay shared.
 */
data class ProxyHomeDialogsState<ServerPayload : Any, GroupPayload : Any, Tool : Any, GroupId : Any>(
    val pendingServerDeletion: ServerPayload? = null,
    val pendingSubscriptionDeletion: GroupPayload? = null,
    val pendingToolDeletion: Tool? = null,
    val qrPayload: ProxyHomeQrPayload? = null,
    val editingSubscriptionGroupId: GroupId? = null,
    val creatingSubscriptionGroup: Boolean = false,
    val creatingManualGroup: Boolean = false,
    val selectingGroupMemberForServer: ServerPayload? = null,
)