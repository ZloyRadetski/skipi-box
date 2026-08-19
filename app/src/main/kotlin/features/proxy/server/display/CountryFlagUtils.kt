// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.display

import app.ProxyServerState
import features.proxy.server.model.StrategyGroup

/**
 * Utility for detecting and extracting country flag emojis from server remarks.
 */
internal object CountryFlagUtils {
    private const val REGIONAL_INDICATOR_MIN = 0x1F1E6
    private const val REGIONAL_INDICATOR_MAX = 0x1F1FF
    private const val BLACK_FLAG = 0x1F3F4
    private const val CANCEL_TAG = 0xE007F

    /**
     * Extracts a leading country flag emoji from [text], ignoring leading whitespace.
     * Returns the flag emoji string if present, or null otherwise.
     */
    fun extractLeadingCountryFlag(text: String): String? {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return null

        // 1. Standard 2-letter country flag (pair of Regional Indicator Symbols, e.g. 🇳🇱, 🇩🇪, 🇺🇸, 🇷🇺)
        if (trimmed.length >= 2) {
            val cp1 = Character.codePointAt(trimmed, 0)
            val count1 = Character.charCount(cp1)
            if (cp1 in REGIONAL_INDICATOR_MIN..REGIONAL_INDICATOR_MAX && trimmed.length >= count1 + 1) {
                val cp2 = Character.codePointAt(trimmed, count1)
                val count2 = Character.charCount(cp2)
                if (cp2 in REGIONAL_INDICATOR_MIN..REGIONAL_INDICATOR_MAX) {
                    return trimmed.substring(0, count1 + count2)
                }
            }
        }

        // 2. Subdivision / tag-based flags (e.g. 🏴󠁧󠁢󠁥󠁮󠁧󠁿 England/Scotland/Wales)
        if (trimmed.length >= 2) {
            val cp1 = Character.codePointAt(trimmed, 0)
            if (cp1 == BLACK_FLAG) {
                var offset = Character.charCount(cp1)
                while (offset < trimmed.length) {
                    val cp = Character.codePointAt(trimmed, offset)
                    val count = Character.charCount(cp)
                    offset += count
                    if (cp == CANCEL_TAG) {
                        return trimmed.substring(0, offset)
                    }
                    if (cp !in 0xE0000..0xE007F) {
                        break
                    }
                }
            }
        }

        // 3. Other common flag emojis
        val commonFlags = listOf("🏳️", "🏴", "🏁", "🚩", "🎌")
        for (flag in commonFlags) {
            if (trimmed.startsWith(flag)) {
                return flag
            }
        }

        return null
    }

    /**
     * Strips the leading country flag emoji (and following spaces) from [text],
     * returning the clean server name. If removing the flag would leave the string empty,
     * returns the original [text].
     */
    fun stripLeadingCountryFlag(text: String): String {
        val flag = extractLeadingCountryFlag(text) ?: return text
        val trimmed = text.trimStart()
        if (trimmed.startsWith(flag)) {
            val rest = trimmed.substring(flag.length).trimStart()
            return if (rest.isNotEmpty()) rest else text
        }
        return text
    }

    /**
     * Checks whether [memberId] is an effective member of [strategyGroup].
     */
    fun strategyGroupContainsMember(
        strategyGroup: StrategyGroup,
        memberId: Int,
        allServers: List<ProxyServerState>,
        visitingStrategyGroupIds: Set<Int> = emptySet(),
    ): Boolean {
        if (strategyGroup.proxyServerIds.isNotEmpty()) {
            if (memberId in strategyGroup.proxyServerIds) return true
            for (id in strategyGroup.proxyServerIds) {
                val member = allServers.firstOrNull { it.id == id }
                val server = member?.server
                if (server is StrategyGroup && id !in visitingStrategyGroupIds) {
                    if (strategyGroupContainsMember(server, memberId, allServers, visitingStrategyGroupIds + id)) {
                        return true
                    }
                }
            }
            return false
        }
        val targetServer = allServers.firstOrNull { it.id == memberId } ?: return false
        if (strategyGroup.subscriptionGroupId != null && targetServer.groupId != strategyGroup.subscriptionGroupId) {
            return false
        }
        val filter = strategyGroup.filter
        if (filter.isNotBlank()) {
            val regex = runCatching { Regex(filter) }.getOrNull()
            val remarks = targetServer.server.getInfo().remarks
            return if (regex != null) {
                regex.containsMatchIn(remarks)
            } else {
                remarks.contains(filter, ignoreCase = true)
            }
        }
        return true
    }
}
