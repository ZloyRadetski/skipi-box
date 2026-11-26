// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings.search

/** One searchable setting with the texts of the current interface language. */
data class SettingsSearchDocument(
    val id: String,
    val title: String,
    val summary: String = "",
    /** The section the setting lives in, as shown beside a result. */
    val path: String = "",
    /** Synonyms and terms of other languages; never shown. */
    val keywords: List<String> = emptyList(),
)

/** A found document; [titleRanges] are the matched parts of its title, in the title's own indices. */
data class SettingsSearchHit(val id: String, val titleRanges: List<IntRange>)

/**
 * Finds [query] in [documents], best first; documents with equal scores keep their order.
 *
 * Case, `ё`, punctuation and Persian letter variants do not matter, words may be partial and come in any order,
 * one typo is forgiven in a longer word, and a query typed on the wrong Russian/English layout is also tried.
 */
fun searchSettings(query: String, documents: List<SettingsSearchDocument>): List<SettingsSearchHit> {
    val typed = SearchQuery(normalize(query).text)
    if (typed.text.isEmpty()) return emptyList()
    val converted = listOf(switchLayout(query, EnglishKeys, RussianKeys), switchLayout(query, RussianKeys, EnglishKeys))
        .map { SearchQuery(normalize(it).text) }
        .filter { it.text.isNotEmpty() && it.text != typed.text }

    return documents
        .mapNotNull { document ->
            val indexed = IndexedDocument(document)
            val direct = indexed.match(typed, forgiveTypos = true)
            // A converted query is a guess: it counts half, and a typo on top of it would match almost anything.
            val guessed = converted.mapNotNull { indexed.match(it, forgiveTypos = false) }
                .maxByOrNull { it.score }
                ?.let { it.copy(score = it.score / 2) }
            listOfNotNull(direct, guessed).maxByOrNull { it.score }?.let { document.id to it }
        }
        .sortedByDescending { (_, match) -> match.score }
        .map { (id, match) -> SettingsSearchHit(id, match.titleRanges) }
}

private const val EnglishKeys = "`qwertyuiop[]asdfghjkl;'zxcvbnm,."
private const val RussianKeys = "ёйцукенгшщзхъфывапролджэячсмитьбю"

/** Shorter words are too close to each other to guess what was meant. */
private const val MinTypoWordLength = 4

/** Shorter parts of alphabetic words only count at the start of a word. */
private const val MinInnerMatchLength = 3

private object Score {
    const val TitleStart = 100
    const val TitleWord = 90
    const val TitleInner = 70
    const val KeywordWord = 60
    const val Path = 40
    const val KeywordInner = 35
    const val Summary = 30
    const val TitleTypo = 20
    const val KeywordTypo = 15
    const val OtherTypo = 10
    const val TitleBeginsWithQuery = 50
}

private fun switchLayout(text: String, from: String, to: String): String = buildString {
    text.forEach { char ->
        val key = from.indexOf(char.lowercaseChar())
        append(if (key >= 0) to[key] else char)
    }
}

/** Comparable text; [origin] holds for every character its index in the source text. */
private class Normalized(val text: String, val origin: IntArray) {
    val words: List<String> = text.split(' ').filter { it.isNotEmpty() }

    /** The text without spaces, and for each of its characters the index in [text]. */
    val squashed: String = text.replace(" ", "")
    val squashedIndex: IntArray = text.indices.filter { text[it] != ' ' }.toIntArray()

    fun startsWord(index: Int): Boolean = index == 0 || text[index - 1] == ' '

    fun sourceRange(start: Int, endInclusive: Int): IntRange = origin[start]..origin[endInclusive]
}

private fun normalize(source: String): Normalized {
    val text = StringBuilder()
    val origin = ArrayList<Int>()
    source.forEachIndexed { index, raw ->
        // Joiners, Arabic vowel marks and the elongation stroke are spelling variants, not separators.
        if (raw == '‌' || raw == '‍' || raw == 'ـ' || raw in 'ً'..'ٟ') return@forEachIndexed
        val char = when (val lower = raw.lowercaseChar()) {
            'ё' -> 'е'
            'ي', 'ى' -> 'ی' // Arabic yeh and alef maksura to Persian yeh
            'ك' -> 'ک' // Arabic kaf to Persian keheh
            in '۰'..'۹' -> '0' + (lower - '۰')
            in '٠'..'٩' -> '0' + (lower - '٠')
            else -> lower
        }
        if (char.isLetterOrDigit()) {
            text.append(char)
            origin.add(index)
        } else if (text.isNotEmpty() && text.last() != ' ') {
            text.append(' ')
            origin.add(index)
        }
    }
    if (text.isNotEmpty() && text.last() == ' ') {
        text.deleteAt(text.lastIndex)
        origin.removeAt(origin.lastIndex)
    }
    return Normalized(text.toString(), origin.toIntArray())
}

private class SearchQuery(val text: String) {
    val words: List<String> = text.split(' ').filter { it.isNotEmpty() }
    val squashed: String = text.replace(" ", "")
}

private data class Match(val score: Int, val titleRanges: List<IntRange>)

/** Chinese and Japanese are written without spaces, so every character may begin a word. */
private fun isUnspaced(char: Char): Boolean = char in '぀'..'ヿ' || char in '㐀'..'鿿'

/** Where [word] occurs in this text, preferring the start of a word; null when it does not count as found. */
private fun Normalized.find(word: String): Int? {
    var inner: Int? = null
    var index = text.indexOf(word)
    while (index >= 0) {
        if (startsWord(index)) return index
        if (inner == null) inner = index
        index = text.indexOf(word, index + 1)
    }
    return inner?.takeIf { word.length >= MinInnerMatchLength || word.any(::isUnspaced) }
}

/** Where [query] begins a word of this text once the spaces of both are ignored, as a range of [Normalized.text]. */
private fun Normalized.findJoined(query: String): IntRange? {
    var index = squashed.indexOf(query)
    while (index >= 0) {
        val start = squashedIndex[index]
        if (startsWord(start)) return start..squashedIndex[index + query.length - 1]
        index = squashed.indexOf(query, index + 1)
    }
    return null
}

private class IndexedDocument(document: SettingsSearchDocument) {
    private val title = normalize(document.title)
    private val summary = normalize(document.summary)
    private val path = normalize(document.path)
    private val keywords = normalize(document.keywords.joinToString(" "))

    fun match(query: SearchQuery, forgiveTypos: Boolean): Match? {
        val byWords = matchWords(query, forgiveTypos)
        val joined = title.findJoined(query.squashed)
        val byJoined = joined?.let { range ->
            Match(
                score = (if (range.first == 0) Score.TitleStart else Score.TitleWord) * query.words.size,
                titleRanges = listOf(title.sourceRange(range.first, range.last)),
            )
        }
        val best = listOfNotNull(byWords, byJoined).maxByOrNull { it.score }
            ?: keywords.findJoined(query.squashed)?.let { Match(Score.KeywordWord, emptyList()) }
            ?: return null
        val begins = title.squashed.startsWith(query.squashed)
        return if (begins) best.copy(score = best.score + Score.TitleBeginsWithQuery) else best
    }

    private fun matchWords(query: SearchQuery, forgiveTypos: Boolean): Match? {
        var score = 0
        val ranges = ArrayList<IntRange>()
        for (word in query.words) {
            val inTitle = title.find(word)
            if (inTitle != null) ranges += title.sourceRange(inTitle, inTitle + word.length - 1)
            val inKeywords = keywords.find(word)
            val exact = maxOf(
                when {
                    inTitle == null -> 0
                    inTitle == 0 -> Score.TitleStart
                    title.startsWord(inTitle) -> Score.TitleWord
                    else -> Score.TitleInner
                },
                when {
                    inKeywords == null -> 0
                    keywords.startsWord(inKeywords) -> Score.KeywordWord
                    else -> Score.KeywordInner
                },
                if (path.find(word) != null) Score.Path else 0,
                if (summary.find(word) != null) Score.Summary else 0,
            )
            val wordScore = when {
                exact > 0 -> exact
                !forgiveTypos || word.length < MinTypoWordLength -> 0
                title.words.any { isOneTypoAway(word, it) } -> Score.TitleTypo
                keywords.words.any { isOneTypoAway(word, it) } -> Score.KeywordTypo
                (path.words + summary.words).any { isOneTypoAway(word, it) } -> Score.OtherTypo
                else -> 0
            }
            if (wordScore == 0) return null
            score += wordScore
        }
        return Match(score, merge(ranges))
    }
}

private fun merge(ranges: List<IntRange>): List<IntRange> {
    val merged = ArrayList<IntRange>()
    for (range in ranges.sortedBy { it.first }) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

/** Whether [typed] is one edit away from [word] or from its beginning, so an unfinished word may carry a typo too. */
private fun isOneTypoAway(typed: String, word: String): Boolean =
    (typed.length - 1..typed.length + 1).any { length ->
        length <= word.length && isOneEdit(typed, word.substring(0, length))
    }

/** One inserted, removed or replaced character, or two neighbours swapped. */
private fun isOneEdit(first: String, second: String): Boolean {
    if (first == second) return false
    val common = first.commonPrefixWith(second).length
    val a = first.substring(common)
    val b = second.substring(common)
    return a.drop(1) == b.drop(1) && a.length == b.length ||
        a.drop(1) == b ||
        b.drop(1) == a ||
        a.length >= 2 && b.length >= 2 && a[0] == b[1] && a[1] == b[0] && a.drop(2) == b.drop(2)
}
