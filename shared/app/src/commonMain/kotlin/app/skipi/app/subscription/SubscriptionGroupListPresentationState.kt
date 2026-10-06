// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

/** Ephemeral presentation state for the subscription-group list and its editor/deletion dialogs. */
data class SubscriptionGroupListPresentationState<DeletionPayload>(
    val editingGroupId: Int? = null,
    val showGroupEditor: Boolean = false,
    val pendingGroupDeletion: DeletionPayload? = null,
    val updatingGroupIds: Set<Int> = emptySet(),
)

/** Prefers a freshly saved payload over a potentially stale composition's ID resolver. */
fun <GroupPayload> resolveSubscriptionGroupUpdatePayload(
    groupId: Int,
    groupOverride: GroupPayload?,
    resolveGroupPayload: (groupId: Int) -> GroupPayload?,
): GroupPayload? = groupOverride ?: resolveGroupPayload(groupId)
