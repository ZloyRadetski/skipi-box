// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** How many saved themes the library keeps; the stored string stays small and the list stays usable. */
const val CustomThemeLibraryLimit = 30

private const val DefaultNamePrefix = "Theme"

/** Reads the stored library: one theme code per line, damaged lines skipped, at most the limit. */
fun String.toCustomThemes(): List<CustomThemeShare> = lineSequence()
    .mapNotNull { line -> line.takeIf(String::isNotBlank)?.let(::parseCustomThemeCodeOrNull) }
    .take(CustomThemeLibraryLimit)
    .toList()

fun List<CustomThemeShare>.toStorage(): String = take(CustomThemeLibraryLimit).joinToString("\n") { it.toCode() }

fun List<CustomThemeShare>.isFull(): Boolean = size >= CustomThemeLibraryLimit

/** The index of a saved theme with exactly these colours and base, whatever its name, or -1. */
fun List<CustomThemeShare>.indexOfColours(theme: CustomThemeShare): Int =
    indexOfFirst { it.copy(name = "") == theme.copy(name = "") }

/**
 * Appends [theme] under a name nobody else has. A theme whose colours are already saved, or a full
 * library, changes nothing.
 */
fun List<CustomThemeShare>.withAdded(theme: CustomThemeShare): List<CustomThemeShare> {
    if (isFull() || indexOfColours(theme) >= 0) return this
    val wanted = theme.name.cleanCustomThemeName().ifEmpty { "$DefaultNamePrefix ${size + 1}" }
    return this + theme.copy(name = uniqueName(wanted, ignoreIndex = -1))
}

fun List<CustomThemeShare>.withRenamed(index: Int, name: String): List<CustomThemeShare> {
    if (index !in indices) return this
    val wanted = name.cleanCustomThemeName().ifEmpty { return this }
    if (this[index].name == wanted) return this
    val unique = uniqueName(wanted, ignoreIndex = index)
    return mapIndexed { at, item -> if (at == index) item.copy(name = unique) else item }
}

fun List<CustomThemeShare>.withoutIndex(index: Int): List<CustomThemeShare> =
    if (index in indices) filterIndexed { at, _ -> at != index } else this

enum class CustomThemeImport { Added, AlreadySaved, LibraryFull }

data class CustomThemeImportResult(val storage: String, val outcome: CustomThemeImport)

/** Adds a theme that arrived from a link or a pasted code to the stored library, and says what happened. */
fun importCustomTheme(storage: String, theme: CustomThemeShare): CustomThemeImportResult {
    val library = storage.toCustomThemes()
    return when {
        library.indexOfColours(theme) >= 0 -> CustomThemeImportResult(storage, CustomThemeImport.AlreadySaved)
        library.isFull() -> CustomThemeImportResult(storage, CustomThemeImport.LibraryFull)
        else -> CustomThemeImportResult(library.withAdded(theme).toStorage(), CustomThemeImport.Added)
    }
}

private fun List<CustomThemeShare>.uniqueName(wanted: String, ignoreIndex: Int): String {
    fun taken(candidate: String) = withIndex().any { (at, item) ->
        at != ignoreIndex && item.name.equals(candidate, ignoreCase = true)
    }
    if (!taken(wanted)) return wanted
    var number = 2
    while (true) {
        val suffix = " ($number)"
        val candidate = wanted.take(CustomThemeNameMaxLength - suffix.length).trimEnd() + suffix
        if (!taken(candidate)) return candidate
        number++
    }
}
