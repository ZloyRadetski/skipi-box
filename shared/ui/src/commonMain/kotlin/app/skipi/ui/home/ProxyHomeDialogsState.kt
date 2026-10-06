// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import app.skipi.app.home.ProxyHomeDialogsState

/**
 * Remembers Home dialog state using the existing host lifecycle contract:
 * only group-editor selection and creation mode survive recreation.
 */
@Composable
fun <ServerPayload : Any, GroupPayload : Any, Tool : Any, GroupId : Any> rememberSaveableProxyHomeDialogsState(): MutableState<ProxyHomeDialogsState<ServerPayload, GroupPayload, Tool, GroupId>> =
    rememberSaveable(stateSaver = proxyHomeDialogsSaver<ServerPayload, GroupPayload, Tool, GroupId>()) {
        mutableStateOf(ProxyHomeDialogsState<ServerPayload, GroupPayload, Tool, GroupId>())
    }

private fun <ServerPayload : Any, GroupPayload : Any, Tool : Any, GroupId : Any> proxyHomeDialogsSaver(): Saver<ProxyHomeDialogsState<ServerPayload, GroupPayload, Tool, GroupId>, List<Any?>> =
    Saver(
        save = { state ->
            listOf(
                state.editingSubscriptionGroupId,
                state.creatingSubscriptionGroup,
                state.creatingManualGroup,
            )
        },
        restore = { values ->
            @Suppress("UNCHECKED_CAST")
            ProxyHomeDialogsState(
                editingSubscriptionGroupId = values.getOrNull(0) as? GroupId,
                creatingSubscriptionGroup = values.getOrNull(1) as? Boolean ?: false,
                creatingManualGroup = values.getOrNull(2) as? Boolean ?: false,
            )
        },
    )