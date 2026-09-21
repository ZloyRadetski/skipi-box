// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.presentation

/**
 * Platform-neutral interaction state for the proxy Home screen.
 *
 * Group identifiers remain strings because Android and desktop have different
 * storage identifiers. Hosts map those identifiers at their boundary, while
 * the shared screen owns the user-facing interaction rules.
 */
data class ProxyHomePresentationState(
    val selectedGroupId: String? = null,
    val searchQuery: String = "",
    val searchVisible: Boolean = false,
)

sealed interface ProxyHomePresentationAction {
    data class SelectGroup(val groupId: String?) : ProxyHomePresentationAction

    data class ChangeSearchQuery(val value: String) : ProxyHomePresentationAction

    data class SetSearchVisible(val visible: Boolean) : ProxyHomePresentationAction

    data object ToggleSearch : ProxyHomePresentationAction
}

/** Applies a Home interaction without depending on Compose or either platform. */
fun ProxyHomePresentationState.reduce(
    action: ProxyHomePresentationAction,
): ProxyHomePresentationState = when (action) {
    is ProxyHomePresentationAction.SelectGroup -> copy(selectedGroupId = action.groupId)
    is ProxyHomePresentationAction.ChangeSearchQuery -> copy(searchQuery = action.value)
    is ProxyHomePresentationAction.SetSearchVisible -> copy(searchVisible = action.visible)
    ProxyHomePresentationAction.ToggleSearch -> copy(searchVisible = !searchVisible)
}
