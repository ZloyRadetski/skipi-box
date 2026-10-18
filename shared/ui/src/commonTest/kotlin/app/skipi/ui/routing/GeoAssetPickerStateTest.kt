// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import features.routing.usecase.GeoSuggestionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoAssetPickerStateTest {
    @Test
    fun visibilityStateStartsEmptyForBothHiddenAndShownDialogKeys() {
        val edited = GeoAssetPickerFilterState.forShow(true).copy(
            searchQuery = "telegram",
            selectedGroup = "Custom",
        )

        assertEquals("telegram", edited.searchQuery)
        assertEquals("Custom", edited.selectedGroup)
        val hidden = GeoAssetPickerFilterState.forShow(false)
        val shown = GeoAssetPickerFilterState.forShow(true)
        assertEquals("", hidden.searchQuery)
        assertNull(hidden.selectedGroup)
        assertEquals("", shown.searchQuery)
        assertNull(shown.selectedGroup)
    }

    @Test
    fun groupsKeepFirstAppearanceOrderAndRemoveDuplicates() {
        val items = listOf(
            item("a", "geosite:a", "GeoSite"),
            item("custom-a", "ext:a.dat:a", "Custom"),
            item("b", "geosite:b", "GeoSite"),
            item("custom-b", "ext:b.dat:b", "Custom"),
        )

        assertEquals(listOf("GeoSite", "Custom"), geoSuggestionGroups(items))
    }

    @Test
    fun searchMatchesTagRuleAndGroupCaseInsensitivelyAfterTrimming() {
        val items = listOf(
            item("Telegram-Channel", "geosite:telegram", "GeoSite"),
            item("direct-list", "https://rules.example/direct.txt", "Provider One"),
            item("private", "ext:private.dat:private", "Private GeoIP"),
        )

        assertEquals(
            listOf(items[0]),
            filterGeoSuggestionItems(items, GeoAssetPickerFilterState.forShow(true).copy(searchQuery = "  TELEGRAM  ")),
        )
        assertEquals(
            listOf(items[1]),
            filterGeoSuggestionItems(items, GeoAssetPickerFilterState.forShow(true).copy(searchQuery = "  DIRECT.TXT ")),
        )
        assertEquals(
            listOf(items[2]),
            filterGeoSuggestionItems(items, GeoAssetPickerFilterState.forShow(true).copy(searchQuery = " private geoip ")),
        )
    }

    @Test
    fun selectedGroupAndSearchAreIntersectedAndUnknownGroupShowsNoItems() {
        val items = listOf(
            item("telegram", "geosite:telegram", "GeoSite"),
            item("telegram-custom", "ext:custom.dat:telegram", "Custom"),
            item("google", "geosite:google", "GeoSite"),
        )

        assertEquals(
            listOf(items[1]),
            filterGeoSuggestionItems(
                items,
                GeoAssetPickerFilterState.forShow(true).copy(searchQuery = "telegram", selectedGroup = "Custom"),
            ),
        )
        assertEquals(
            emptyList(),
            filterGeoSuggestionItems(
                items,
                GeoAssetPickerFilterState.forShow(true).copy(selectedGroup = "Missing"),
            ),
        )
    }

    @Test
    fun selectedItemCallbackRunsBeforeDialogDismissCallback() {
        val events = mutableListOf<String>()
        val selected = item("telegram", "geosite:telegram", "GeoSite")

        selectGeoSuggestionThenDismiss(
            selected,
            onSelect = { item -> events += "selected:${item.fullRule}" },
            onDismiss = { events += "dismissed" },
        )

        assertEquals(listOf("selected:geosite:telegram", "dismissed"), events)
    }

    private fun item(tag: String, fullRule: String, groupName: String) =
        GeoSuggestionItem(tag, fullRule, groupName, isCustom = groupName != "GeoSite")
}
