// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import features.routing.usecase.GeoSuggestionItem

data class GeoAssetPickerFilterState(
    val searchQuery: String = "",
    val selectedGroup: String? = null,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun forShow(show: Boolean): GeoAssetPickerFilterState {
            // The visibility key mirrors the Android dialog's remember(show) reset behavior.
            return GeoAssetPickerFilterState()
        }
    }
}

fun geoSuggestionGroups(items: List<GeoSuggestionItem>): List<String> =
    items.map(GeoSuggestionItem::groupName).distinct()

fun filterGeoSuggestionItems(
    items: List<GeoSuggestionItem>,
    state: GeoAssetPickerFilterState,
): List<GeoSuggestionItem> {
    val query = state.searchQuery.trim()
    return items.filter { item ->
        val matchesGroup = state.selectedGroup == null || item.groupName == state.selectedGroup
        val matchesSearch = query.isBlank() ||
            item.tag.contains(query, ignoreCase = true) ||
            item.fullRule.contains(query, ignoreCase = true) ||
            item.groupName.contains(query, ignoreCase = true)
        matchesGroup && matchesSearch
    }
}

fun selectGeoSuggestionThenDismiss(
    item: GeoSuggestionItem,
    onSelect: (GeoSuggestionItem) -> Unit,
    onDismiss: () -> Unit,
) {
    onSelect(item)
    onDismiss()
}
