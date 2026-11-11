// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkipiMenuModelTest {
    @Test
    fun everyEntryBecomesASectionAndKeepsItsItemsInOrder() {
        val sections = listOf(
            DropdownEntry(items = listOf(DropdownItem(text = "A"), DropdownItem(text = "B"))),
            DropdownEntry(items = listOf(DropdownItem(text = "C"))),
        ).toMenuSections()

        assertEquals(listOf(listOf("A", "B"), listOf("C")), sections.map { section -> section.items.map { it.title } })
    }

    @Test
    fun anItemKeepsItsSummarySelectionAndAction() {
        var clicked = 0
        val item = listOf(
            DropdownEntry(items = listOf(DropdownItem(text = "A", summary = "about A", selected = true, onClick = { clicked++ }))),
        ).toMenuSections().single().items.single()

        assertEquals("about A", item.summary)
        assertTrue(item.selected)
        item.onClick?.invoke()
        assertEquals(1, clicked)
    }

    @Test
    fun aDisabledEntryDisablesEveryItemInIt() {
        val sections = listOf(
            DropdownEntry(items = listOf(DropdownItem(text = "A"), DropdownItem(text = "B", enabled = false)), enabled = false),
            DropdownEntry(items = listOf(DropdownItem(text = "C"), DropdownItem(text = "D", enabled = false))),
        ).toMenuSections()

        assertEquals(listOf(false, false), sections[0].items.map { it.enabled })
        assertEquals(listOf(true, false), sections[1].items.map { it.enabled })
    }

    @Test
    fun theChildrenOfAnItemAreConvertedToo() {
        val parent = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = "Sort", children = listOf(DropdownItem(text = "By name"), DropdownItem(text = "By ping"))),
                ),
            ),
        ).toMenuSections().single().items.single()

        assertEquals(listOf("By name", "By ping"), parent.children.map { it.title })
        assertTrue(parent.hasChildren)
    }

    @Test
    fun anEmptyChildListIsNotASubmenu() {
        val item = listOf(DropdownEntry(items = listOf(DropdownItem(text = "A", children = emptyList())))).toMenuSections()
            .single().items.single()

        assertFalse(item.hasChildren)
    }

    @Test
    fun emptyEntriesAreDroppedSoTheyDoNotLeaveALoneDivider() {
        val sections = listOf(
            DropdownEntry(items = emptyList()),
            DropdownEntry(items = listOf(DropdownItem(text = "A"))),
        ).toMenuSections()

        assertEquals(1, sections.size)
    }

    @Test
    fun aListOfStringsIsOneSectionWithTheChosenOneSelected() {
        val chosen = mutableListOf<Int>()
        val section = listOf("Auto", "Light", "Dark").toMenuSection(selectedIndex = 1) { chosen += it }

        assertEquals(listOf(false, true, false), section.items.map { it.selected })
        section.items[2].onClick?.invoke()
        assertEquals(listOf(2), chosen)
    }

    @Test
    fun theValueShownOnAPreferenceRowIsTheSelectedItemOfTheFirstSectionThatHasOne() {
        val sections = listOf(
            DropdownEntry(items = listOf(DropdownItem(text = "A"), DropdownItem(text = "B"))),
            DropdownEntry(items = listOf(DropdownItem(text = "C"), DropdownItem(text = "D", selected = true))),
        ).toMenuSections()

        assertEquals("D", sections.selectedTitle())
        assertNull(listOf(DropdownEntry(items = listOf(DropdownItem(text = "A")))).toMenuSections().selectedTitle())
    }

    @Test
    fun aMenuWithOneSectionClosesWhenAnItemIsChosenAndOneWithSeveralStaysOpen() {
        assertTrue(closesOnSelection(sectionCount = 1))
        assertFalse(closesOnSelection(sectionCount = 2))
    }
}

class SkipiMenuPathTest {
    private val sort = SkipiMenuItem(
        title = "Sort",
        key = "sort",
        children = listOf(
            SkipiMenuItem(title = "By ping", key = "ping"),
            SkipiMenuItem(title = "Order", key = "order", children = listOf(SkipiMenuItem(title = "Down", key = "down"))),
        ),
    )
    private val sections = listOf(SkipiMenuSection(listOf(SkipiMenuItem(title = "Refresh", key = "refresh"), sort)))

    @Test
    fun anEmptyPathIsTheMenuItself() {
        assertEquals(emptyList(), emptyList<String>().openedRows(sections))
    }

    @Test
    fun aPathOfKeysLeadsThroughTheRowsThatWereOpened() {
        assertEquals(listOf("Sort"), listOf("sort").openedRows(sections).map { it.title })
        assertEquals(listOf("Sort", "Order"), listOf("sort", "order").openedRows(sections).map { it.title })
    }

    @Test
    fun theWalkStopsAtAKeyThatIsGoneOrIsNotADoor() {
        assertEquals(listOf("Sort"), listOf("sort", "gone", "order").openedRows(sections).map { it.title })
        assertEquals(emptyList(), listOf("refresh").openedRows(sections))
    }
}
