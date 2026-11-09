// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomThemeLibraryTest {
    private fun theme(name: String, accent: Long = 0xFF3B82F6, dark: Boolean = true) = CustomThemeShare(
        name = name,
        dark = dark,
        accent = accent,
        background = 0xFF081427,
        surface = 0xFF102443,
        surfaceVariant = 0xFF18345E,
        text = 0xFFE7F1FF,
        textSecondary = 0xFFB2C8EA,
    )

    @Test
    fun anEmptyOrBlankStorageIsAnEmptyLibrary() {
        assertEquals(emptyList(), "".toCustomThemes())
        assertEquals(emptyList(), " \n \n".toCustomThemes())
    }

    @Test
    fun aLibraryRoundTripsThroughItsStorageString() {
        val library = listOf(theme("One"), theme("Two", accent = 0xFFB93872))

        assertEquals(library, library.toStorage().toCustomThemes())
    }

    @Test
    fun damagedLinesAreSkippedAndTheRestIsKept() {
        val good = theme("Good")
        val storage = "garbage\n${good.toCode()}\n!!!\n"

        assertEquals(listOf(good), storage.toCustomThemes())
    }

    @Test
    fun aStorageHoldsNoMoreThanTheLimit() {
        val many = (1..CustomThemeLibraryLimit + 5).map { theme("T$it", accent = 0xFF000000 + it) }

        assertEquals(CustomThemeLibraryLimit, many.toStorage().toCustomThemes().size)
    }

    @Test
    fun addingKeepsTheNewThemeLastAndGivesAUniqueName() {
        val library = listOf(theme("Night"))

        val next = library.withAdded(theme("Night", accent = 0xFFB93872))

        assertEquals(listOf("Night", "Night (2)"), next.map { it.name })
    }

    @Test
    fun aThemeWithoutANameGetsADefaultOne() {
        val next = emptyList<CustomThemeShare>().withAdded(theme(""))

        assertEquals("Theme 1", next.single().name)
    }

    @Test
    fun addingTheSameColoursAgainDoesNotMakeADuplicate() {
        val library = listOf(theme("Night"))

        assertEquals(library, library.withAdded(theme("Other name")))
    }

    @Test
    fun aFullLibraryRefusesAnotherTheme() {
        val full = (1..CustomThemeLibraryLimit).map { theme("T$it", accent = 0xFF000000 + it) }

        assertEquals(full, full.withAdded(theme("One more", accent = 0xFF123456)))
        assertTrue(full.isFull())
    }

    @Test
    fun renamingChangesOnlyTheNameAndKeepsItUnique() {
        val library = listOf(theme("A"), theme("B", accent = 0xFFB93872))

        val renamed = library.withRenamed(index = 1, name = "A")

        assertEquals(listOf("A", "A (2)"), renamed.map { it.name })
        assertEquals(library[1].copy(name = "A (2)"), renamed[1])
    }

    @Test
    fun renamingToTheSameNameKeepsItAsItIs() {
        val library = listOf(theme("A"), theme("B", accent = 0xFFB93872))

        assertEquals(library, library.withRenamed(index = 1, name = "B"))
    }

    @Test
    fun removingDropsOnlyThatTheme() {
        val library = listOf(theme("A"), theme("B", accent = 0xFFB93872), theme("C", accent = 0xFF16835A))

        assertEquals(listOf("A", "C"), library.withoutIndex(1).map { it.name })
        assertEquals(library, library.withoutIndex(7))
    }

    @Test
    fun aThemeIsFoundByItsColoursWhateverItsName() {
        val library = listOf(theme("A"), theme("B", accent = 0xFFB93872))

        assertEquals(1, library.indexOfColours(theme("anything", accent = 0xFFB93872)))
        assertEquals(-1, library.indexOfColours(theme("A", accent = 0xFF000001)))
    }

    @Test
    fun importingAddsTheThemeToTheStoredLibrary() {
        val result = importCustomTheme(storage = listOf(theme("One")).toStorage(), theme = theme("Two", accent = 0xFFB93872))

        assertEquals(CustomThemeImport.Added, result.outcome)
        assertEquals(listOf("One", "Two"), result.storage.toCustomThemes().map { it.name })
    }

    @Test
    fun importingColoursThatAreAlreadySavedChangesNothing() {
        val storage = listOf(theme("One")).toStorage()

        val result = importCustomTheme(storage, theme("Renamed"))

        assertEquals(CustomThemeImport.AlreadySaved, result.outcome)
        assertEquals(storage, result.storage)
    }

    @Test
    fun importingIntoAFullLibraryChangesNothing() {
        val storage = (1..CustomThemeLibraryLimit).map { theme("T$it", accent = 0xFF000000 + it) }.toStorage()

        val result = importCustomTheme(storage, theme("One more", accent = 0xFF123456))

        assertEquals(CustomThemeImport.LibraryFull, result.outcome)
        assertEquals(storage, result.storage)
    }
}
