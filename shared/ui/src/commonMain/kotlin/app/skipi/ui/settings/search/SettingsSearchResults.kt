// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import app.skipi.app.settings.search.SettingsSearchDocument
import app.skipi.app.settings.search.searchSettings
import org.jetbrains.compose.resources.stringResource

/** A found setting in the words of the current language. */
class SettingsSearchResult(
    val entry: SettingsSearchEntry,
    val title: String,
    /** The page, or for a page the section of the overview. */
    val path: String,
    val titleRanges: List<IntRange>,
)

/** The entries matching [query], best first. */
@Composable
fun rememberSettingsSearchResults(query: String, entries: List<SettingsSearchEntry>): List<SettingsSearchResult> {
    val documents = entries.map { entry ->
        key(entry.id) {
            SettingsSearchDocument(
                id = entry.id,
                title = entry.title?.let { stringResource(it) } ?: entry.name,
                summary = entry.summary?.let { stringResource(it) }.orEmpty(),
                path = entry.section?.let { stringResource(it) }.orEmpty(),
                keywords = entry.keywords,
            )
        }
    }
    return remember(query, documents) {
        val byId = entries.associateBy { it.id }
        val documentById = documents.associateBy { it.id }
        // Equal matches keep the order they are given in: a whole page before a single row.
        val pagesFirst = documents.sortedByDescending { byId.getValue(it.id).isPage }
        searchSettings(query, pagesFirst).map { hit ->
            val document = documentById.getValue(hit.id)
            SettingsSearchResult(byId.getValue(hit.id), document.title, document.path, hit.titleRanges)
        }
    }
}
