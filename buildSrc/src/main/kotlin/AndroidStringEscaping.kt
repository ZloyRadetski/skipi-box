// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

private val Backslash: Char = 92.toChar()

/**
 * Compose resources keep a string as it is written (`don't`, `say "hi"`), but Android resources want the
 * apostrophe and the double quote escaped. The shared files are written the Compose way; the copy that
 * Android reads is escaped here. Characters inside XML tags (attribute quotes) are left alone, and an
 * already escaped character is not escaped twice.
 */
fun escapeForAndroidResources(line: String): String {
    val tag = Regex("<[^>]*>")
    val out = StringBuilder()
    var last = 0
    fun appendText(text: String) {
        var escaped = false
        for (ch in text) {
            if ((ch == '\'' || ch == '"') && !escaped) out.append(Backslash)
            out.append(ch)
            escaped = ch == Backslash && !escaped
        }
    }
    for (match in tag.findAll(line)) {
        appendText(line.substring(last, match.range.first))
        out.append(match.value)
        last = match.range.last + 1
    }
    appendText(line.substring(last))
    return out.toString()
}
