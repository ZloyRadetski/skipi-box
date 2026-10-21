// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import app.skipi.ui.subscription.SubscriptionGroupUiState
import features.subscription.DefaultSubscriptionGroupId

/** Projects subscription groups into the strategy editor's group selector options. */
fun projectProxyServerEditorGroupOptions(
    groups: List<SubscriptionGroupUiState>,
    allGroupsLabel: String,
    defaultGroupName: String,
): List<ProxyServerEditorGroupOption> =
    listOf(ProxyServerEditorGroupOption(id = null, label = allGroupsLabel)) +
        groups
            .filter { group -> group.enabled || group.builtIn || group.id == DefaultSubscriptionGroupId }
            .map { group ->
                ProxyServerEditorGroupOption(
                    id = group.id,
                    label = group.name.ifBlank { defaultGroupName },
                )
            }
