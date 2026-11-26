// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {
    private val documents = listOf(
        SettingsSearchDocument(
            id = "traffic",
            title = "Уведомление о трафике",
            summary = "Показывать скорость в шторке",
            path = "Общие",
            keywords = listOf("notification", "traffic"),
        ),
        SettingsSearchDocument(
            id = "proxy",
            title = "Локальный прокси",
            summary = "SOCKS5 и HTTP",
            path = "Сеть",
            keywords = listOf("proxy", "socks", "порт", "port"),
        ),
        SettingsSearchDocument(
            id = "theme",
            title = "Тема оформления",
            summary = "Светлая или тёмная",
            path = "Внешний вид",
            keywords = listOf("theme", "dark", "цвет"),
        ),
        SettingsSearchDocument(
            id = "battery",
            title = "Оптимизация батареи",
            summary = "Разрешить работу прокси в фоне",
            path = "VPN",
            keywords = listOf("battery", "энергосбережение"),
        ),
        SettingsSearchDocument(id = "wifi", title = "Wi-Fi автоматизация"),
        SettingsSearchDocument(id = "fa", title = "پراکسی محلی کاربر"),
        SettingsSearchDocument(id = "fa_zwnj", title = "گزینه‌ها"),
        SettingsSearchDocument(id = "zh", title = "本地代理端口"),
    )

    private fun ids(query: String): List<String> = searchSettings(query, documents).map { it.id }

    @Test
    fun blankOrPunctuationQueryFindsNothing() {
        assertEquals(emptyList(), ids(""))
        assertEquals(emptyList(), ids("   "))
        assertEquals(emptyList(), ids(" - / "))
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals("proxy", ids("ЛОКАЛЬНЫЙ").first())
        assertEquals("proxy", ids("SoCkS").first())
    }

    @Test
    fun yoEqualsYe() {
        assertEquals(listOf("theme"), ids("темная"))
        assertEquals(listOf("theme"), ids("тЁмная"))
    }

    @Test
    fun extraSpacesAndHyphensAreIgnored() {
        assertEquals(listOf("wifi"), ids("  wifi  "))
        assertEquals(listOf("wifi"), ids("wi fi"))
        assertEquals(listOf("wifi"), ids("WI-FI"))
    }

    @Test
    fun wordsMatchInAnyOrderAndPartially() {
        assertEquals(listOf("traffic"), ids("увед траф"))
        assertEquals(listOf("traffic"), ids("траф увед"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(emptyList(), ids("локальный батарея"))
    }

    @Test
    fun summaryPathAndKeywordsAreSearched() {
        assertEquals(listOf("traffic"), ids("шторке"))
        assertEquals(listOf("theme"), ids("внешний"))
        assertEquals(listOf("battery"), ids("энергосбережение"))
        assertEquals(listOf("theme"), ids("dark"))
    }

    @Test
    fun titleRanksAboveKeywordAndSummary() {
        assertEquals(listOf("proxy", "battery"), ids("прокси"))
    }

    @Test
    fun titleStartRanksAboveTitleMiddle() {
        val docs = listOf(
            SettingsSearchDocument(id = "middle", title = "Сброс порта"),
            SettingsSearchDocument(id = "start", title = "Порт прокси"),
        )
        assertEquals(listOf("start", "middle"), searchSettings("порт", docs).map { it.id })
    }

    @Test
    fun equalScoresKeepCatalogOrder() {
        val docs = listOf(SettingsSearchDocument("b", "Порт HTTP"), SettingsSearchDocument("a", "Порт SOCKS"))
        assertEquals(listOf("b", "a"), searchSettings("порт", docs).map { it.id })
    }

    @Test
    fun wrongKeyboardLayoutIsCorrectedBothWays() {
        // "прокси" typed on the English layout, "theme" typed on the Russian one.
        assertEquals("proxy", ids("ghjrcb").first())
        assertEquals(listOf("theme"), ids("еруьу"))
        // The Russian letters that sit on English punctuation keys.
        assertEquals(listOf("battery"), ids(",fnfhtb"))
    }

    @Test
    fun oneTypoIsForgivenInLongerWords() {
        assertEquals("proxy", ids("локльный").first()) // a letter missing
        assertEquals("proxy", ids("локаальный").first()) // a letter doubled
        assertEquals("proxy", ids("локольный").first()) // a letter replaced
        assertEquals("proxy", ids("лоакльный").first()) // two letters swapped
        assertEquals(listOf("battery"), ids("батпреи"))
        assertEquals(listOf("theme"), ids("thmee"))
    }

    @Test
    fun typosAreNotForgivenInShortWordsOrTwice() {
        assertEquals(emptyList(), ids("тмз"))
        assertEquals(emptyList(), ids("лклный"))
    }

    @Test
    fun exactMatchRanksAboveTypoMatch() {
        val docs = listOf(
            SettingsSearchDocument(id = "fuzzy", title = "Порты"),
            SettingsSearchDocument(id = "exact", title = "Тема", summary = "торты"),
        )
        assertEquals(listOf("exact", "fuzzy"), searchSettings("торты", docs).map { it.id })
    }

    @Test
    fun persianLetterVariantsAndJoinersAreUnified() {
        // Arabic kaf and yeh typed for the Persian ones.
        assertEquals(listOf("fa"), ids("پراكسي"))
        assertEquals(listOf("fa"), ids("كاربر"))
        assertEquals(listOf("fa_zwnj"), ids("گزینهها"))
        assertEquals(listOf("fa_zwnj"), ids("گزینه ها"))
    }

    @Test
    fun chineseSubstringsMatchWithoutSpaces() {
        assertEquals(listOf("zh"), ids("代理"))
        assertEquals(listOf("zh"), ids("端"))
    }

    @Test
    fun aSingleLetterOnlyStartsWords() {
        assertEquals(listOf("battery"), ids("б"))
    }

    @Test
    fun titleRangesPointAtTheOriginalText() {
        val hit = searchSettings("траф увед", documents).single()
        val title = "Уведомление о трафике"
        assertEquals(listOf("Увед", "траф"), hit.titleRanges.map { title.substring(it.first, it.last + 1) })
    }

    @Test
    fun titleRangesSurviveRemovedAndReplacedCharacters() {
        val title = "Wi-Fi: тёмная"
        val docs = listOf(SettingsSearchDocument("x", title))
        fun marked(query: String) = searchSettings(query, docs).single().titleRanges
            .map { title.substring(it.first, it.last + 1) }
        assertEquals(listOf("Wi-Fi"), marked("wifi"))
        assertEquals(listOf("тёмн"), marked("темн"))
    }

    @Test
    fun matchOutsideTheTitleHasNoRanges() {
        assertTrue(searchSettings("шторке", documents).single().titleRanges.isEmpty())
    }
}
