// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

/**
 * Index-based view of the outbound options for a dropdown preference.
 *
 * A selected tag that no option matches (for example a rule pointing at a removed server)
 * is appended as an extra entry labelled with the raw tag, so it stays visible and selected.
 */
internal class RouteRuleOutboundChoice(
    options: List<RouteRuleOutboundItem>,
    selectedTag: String,
) {
    private val tags: List<String>

    val labels: List<String>
    val selectedIndex: Int

    init {
        val known = options.indexOfFirst { it.tag == selectedTag }
        if (known >= 0) {
            tags = options.map { it.tag }
            labels = options.map { it.label }
            selectedIndex = known
        } else {
            tags = options.map { it.tag } + selectedTag
            labels = options.map { it.label } + selectedTag
            selectedIndex = options.size
        }
    }

    fun tagAt(index: Int): String? = tags.getOrNull(index)
}
