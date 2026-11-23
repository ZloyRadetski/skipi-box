// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import features.logs.CoreLogEntry

/**
 * A log search query. Words are matched anywhere in the shown line, ignoring case (and ё/е); every
 * word must be present, in any order. `"quoted text"` is one word, `-word` excludes lines that
 * contain it, and `level:error` (also warn, info, debug) keeps only that level.
 */
class LogSearch private constructor(
    private val terms: List<String>,
    private val excluded: List<String>,
    private val levels: Set<String>,
) {
    val isEmpty: Boolean get() = terms.isEmpty() && excluded.isEmpty() && levels.isEmpty()

    // Value equality: parsing the same query twice must not look like a new search to remember() and effects.
    override fun equals(other: Any?): Boolean =
        this === other || (other is LogSearch && terms == other.terms && excluded == other.excluded && levels == other.levels)

    override fun hashCode(): Int = 31 * (31 * terms.hashCode() + excluded.hashCode()) + levels.hashCode()

    /** [text] is the line as shown, so the time and the level can be searched too. */
    fun matches(entry: CoreLogEntry, text: String): Boolean {
        if (levels.isNotEmpty() && normalizeLevel(entry.level) !in levels) return false
        if (terms.isEmpty() && excluded.isEmpty()) return true
        val haystack = text.fold()
        return terms.all { it in haystack } && excluded.none { it in haystack }
    }

    /** Where the positive words occur in [text], for highlighting; overlapping ranges are merged. */
    fun highlightRanges(text: String): List<IntRange> {
        if (terms.isEmpty()) return emptyList()
        val haystack = text.fold()
        val found = terms.flatMap { term ->
            buildList {
                var from = haystack.indexOf(term)
                while (from >= 0) {
                    add(from..from + term.length - 1)
                    from = haystack.indexOf(term, from + term.length)
                }
            }
        }.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (range in found) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    companion object {
        val None = LogSearch(emptyList(), emptyList(), emptySet())

        fun parse(query: String): LogSearch {
            val terms = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            val levels = mutableSetOf<String>()
            for (token in tokenize(query)) {
                val lower = token.lowercase()
                when {
                    // A lone "-" is a word still being typed, not something to look for.
                    token == "-" -> Unit
                    lower.startsWith("level:") && lower.length > "level:".length ->
                        levels += normalizeLevel(lower.removePrefix("level:"))
                    token.startsWith("-") && token.length > 1 -> excluded += token.substring(1).fold()
                    else -> terms += token.fold()
                }
            }
            return LogSearch(terms, excluded, levels)
        }

        private fun tokenize(query: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            fun flush() {
                if (current.isNotEmpty()) tokens += current.toString()
                current.clear()
            }
            for (char in query) {
                when {
                    char == '"' -> quoted = !quoted
                    char.isWhitespace() && !quoted -> flush()
                    else -> current.append(char)
                }
            }
            flush()
            return tokens
        }

        private fun normalizeLevel(level: String): String = when (val lower = level.trim().lowercase()) {
            "warn" -> "warning"
            "err", "fatal", "panic" -> "error"
            else -> lower
        }
    }
}

// ё and е are one letter for a search; lowercase keeps the string the same length, so ranges map back.
private fun String.fold(): String = lowercase().replace('ё', 'е')
