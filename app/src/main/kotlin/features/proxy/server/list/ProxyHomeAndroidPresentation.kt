// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import app.skipi.app.home.ProxyHomePresentation

/** Android-only saveable bridge for the shared, platform-neutral Home presentation model. */
@Composable
internal fun rememberProxyHomePresentation(initialSelectedGroupId: Int): MutableState<ProxyHomePresentation> =
    rememberSaveable(stateSaver = ProxyHomePresentationSaver) {
        mutableStateOf(ProxyHomePresentation(selectedGroupId = initialSelectedGroupId.toString()))
    }

private val ProxyHomePresentationSaver = Saver<ProxyHomePresentation, List<Any?>>(
    save = { state ->
        listOf(
            state.selectedGroupId,
            state.searchQuery,
            state.isSearchVisible,
            state.collapsedSubscriptionGroupIds.toList(),
        )
    },
    restore = { values ->
        ProxyHomePresentation(
            selectedGroupId = values.getOrNull(0) as? String,
            searchQuery = values.getOrNull(1) as? String ?: "",
            isSearchVisible = values.getOrNull(2) as? Boolean ?: false,
            collapsedSubscriptionGroupIds = (values.getOrNull(3) as? List<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
                .orEmpty(),
        )
    },
)
