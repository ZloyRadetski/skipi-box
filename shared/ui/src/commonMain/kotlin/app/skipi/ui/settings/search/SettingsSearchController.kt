// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** A row a page was opened to show: [chain] is its anchor, then the anchors of the settings that show it. */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class SettingsSearchFocus(val page: SettingsSearchPage, val chain: List<String>) {
    /** The anchor lit at the moment. */
    var highlighted: String? by mutableStateOf(null)
        internal set

    /** The anchors of the chain that are composed now. */
    internal val anchors = mutableMapOf<String, SettingsSearchAnchorHandle>()
}

/** What the page needs of a composed anchor to scroll it into view. */
@OptIn(ExperimentalFoundationApi::class)
internal class SettingsSearchAnchorHandle {
    val requester = BringIntoViewRequester()
    var height = 0
}

/** The state of the settings search, which outlives the overview while one of its results is open. */
@Stable
class SettingsSearchController {
    var query: String by mutableStateOf("")

    /** The row the next page should show; null once it has been shown. */
    var focus: SettingsSearchFocus? by mutableStateOf(null)
        private set

    /** Set while the host waits for the search field to take the keyboard. */
    var searchFieldRequested: Boolean by mutableStateOf(false)
        private set

    /** Remembers which row of [entry]'s page to show; the host opens the page itself. */
    fun open(entry: SettingsSearchEntry, entries: List<SettingsSearchEntry>) {
        if (entry.isPage) {
            focus = null
            return
        }
        val byId = entries.associateBy { it.id }
        val chain = generateSequence(entry) { current -> current.parent?.let(byId::get) }.map { it.id }.toList()
        focus = SettingsSearchFocus(entry.page, chain)
    }

    fun clearFocus() {
        focus = null
    }

    fun requestSearchField() {
        searchFieldRequested = true
    }

    fun searchFieldFocused() {
        searchFieldRequested = false
    }
}

/** One controller serves the whole application; a test or a preview provides its own. */
private val ApplicationSettingsSearch = SettingsSearchController()

val LocalSettingsSearchController = staticCompositionLocalOf { ApplicationSettingsSearch }
