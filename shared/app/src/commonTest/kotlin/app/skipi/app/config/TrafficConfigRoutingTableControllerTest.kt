// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.clipboard.ClipboardImportMode
import features.config.RoutingTableRow
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.parseV2rayRoutingImportOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrafficConfigRoutingTableControllerTest {
    private val raw = """
        [General]
        ipv6 = true

        [Rule]
        # first
        DOMAIN-SUFFIX,a.com,DIRECT // Alpha
        DST-PORT,22,DIRECT
        RULE-SET,https://example.com/list.txt,PROXY
        FINAL,PROXY

        [Host]
        a = 1.1.1.1
    """.trimIndent() + "\n"

    private class Store(var profile: TrafficConfigState) : TrafficConfigEditorProfileStore {
        override fun update(profileId: Int, transform: (TrafficConfigState) -> TrafficConfigState): Boolean {
            if (profile.id != profileId) return false
            profile = transform(profile)
            return true
        }
    }

    private val store = Store(TrafficConfigState(id = 7, name = "P", rawConfig = raw))
    private val controller = TrafficConfigEditorController(store)

    private fun lines() = store.profile.rawConfig.analyzeShadowrocketConfig().rules.map { it.raw.trim() }

    @Test
    fun aNewRowIsAddedBeforeFinal() {
        val saved = controller.saveRoutingRow(7, lineNumber = null, row = RoutingTableRow(name = "New", policy = "PROXY", domains = listOf("domain:new.com"), port = "443"))

        assertIs<TrafficConfigEditorResult.Saved>(saved.result)
        assertEquals(
            listOf(
                "DOMAIN-SUFFIX,a.com,DIRECT // Alpha",
                "DST-PORT,22,DIRECT",
                "RULE-SET,https://example.com/list.txt,PROXY",
                "AND,((DOMAIN-SUFFIX,new.com),(DST-PORT,443)),PROXY // New",
                "FINAL,PROXY",
            ),
            lines(),
        )
    }

    @Test
    fun theSavedRowReportsItsLineSoTheTableCanKeepItSelected() {
        val added = controller.saveRoutingRow(7, lineNumber = null, row = RoutingTableRow(policy = "PROXY", domains = listOf("domain:new.com")))
        assertEquals(9, added.lineNumber)
        assertEquals("DOMAIN-SUFFIX,new.com,PROXY", store.profile.rawConfig.lines()[added.lineNumber!! - 1])

        val edited = controller.saveRoutingRow(7, lineNumber = 6, row = RoutingTableRow(policy = "PROXY", port = "80"))
        assertEquals(6, edited.lineNumber)

        val empty = controller.saveRoutingRow(7, lineNumber = null, row = RoutingTableRow(policy = "PROXY"))
        assertEquals(null, empty.lineNumber)
    }

    @Test
    fun anEditedRowReplacesItsLineInPlace() {
        val saved = controller.saveRoutingRow(7, lineNumber = 6, row = RoutingTableRow(name = "Alpha", policy = "PROXY", domains = listOf("domain:a.com", "domain:b.com")))

        assertIs<TrafficConfigEditorResult.Saved>(saved.result)
        assertEquals("OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com)),PROXY // Alpha", lines().first())
        assertEquals(4, lines().size)
        assertTrue(store.profile.rawConfig.contains("# first\nOR,"))
    }

    @Test
    fun aRowWithoutConditionsIsNotSaved() {
        val before = store.profile.rawConfig
        val saved = controller.saveRoutingRow(7, lineNumber = null, row = RoutingTableRow(name = "Empty", policy = "PROXY"))

        assertIs<TrafficConfigEditorResult.Skipped>(saved.result)
        assertEquals(before, store.profile.rawConfig)
    }

    @Test
    fun valuesThatCannotBeWrittenAreReportedAndTheRestIsSaved() {
        val saved = controller.saveRoutingRow(7, lineNumber = null, row = RoutingTableRow(policy = "DIRECT", domains = listOf("domain:ok.com", "regexp:^(a|b)$")))

        assertIs<TrafficConfigEditorResult.Saved>(saved.result)
        assertEquals(listOf("regexp:^(a|b)$"), saved.unwritable)
        assertTrue(lines().contains("DOMAIN-SUFFIX,ok.com,DIRECT"))
    }

    @Test
    fun aMissingProfileIsReported() {
        val saved = controller.saveRoutingRow(99, lineNumber = null, row = RoutingTableRow(policy = "DIRECT", port = "1"))
        assertIs<TrafficConfigEditorResult.Missing>(saved.result)
    }

    @Test
    fun switchingARuleOffAndOnKeepsItsPlace() {
        controller.setRoutingRuleEnabled(7, lineNumber = 7, enabled = false)
        assertEquals("DST-PORT,22,DIRECT,skipi-disabled", lines()[1])

        controller.setRoutingRuleEnabled(7, lineNumber = 7, enabled = true)
        assertEquals("DST-PORT,22,DIRECT", lines()[1])
    }

    @Test
    fun aRuleThatIsNotARowCanBeSwitchedToo() {
        controller.setRoutingRuleEnabled(7, lineNumber = 8, enabled = false)
        assertEquals("RULE-SET,https://example.com/list.txt,PROXY,skipi-disabled", lines()[2])
    }

    @Test
    fun deletingARemovesOnlyThatLine() {
        controller.deleteRoutingRule(7, lineNumber = 7)
        assertEquals(listOf("DOMAIN-SUFFIX,a.com,DIRECT // Alpha", "RULE-SET,https://example.com/list.txt,PROXY", "FINAL,PROXY"), lines())
        assertTrue(store.profile.rawConfig.contains("# first"))
        assertTrue(store.profile.rawConfig.contains("[Host]\na = 1.1.1.1"))
    }

    @Test
    fun movingARuleChangesTheOrderOfTheRules() {
        controller.moveRoutingRule(7, fromRuleIndex = 0, toRuleIndex = 2)
        assertEquals(
            listOf("DST-PORT,22,DIRECT", "RULE-SET,https://example.com/list.txt,PROXY", "DOMAIN-SUFFIX,a.com,DIRECT // Alpha", "FINAL,PROXY"),
            lines(),
        )
    }

    @Test
    fun theFinalPolicyIsChangedInPlaceOrCreated() {
        controller.setRoutingFinalPolicy(7, "DIRECT")
        assertEquals("FINAL,DIRECT", lines().last())

        val without = Store(TrafficConfigState(id = 8, name = "Q", rawConfig = "[Rule]\nDST-PORT,22,DIRECT\n"))
        TrafficConfigEditorController(without).setRoutingFinalPolicy(8, "PROXY")
        assertEquals(listOf("DST-PORT,22,DIRECT", "FINAL,PROXY"), without.profile.rawConfig.analyzeShadowrocketConfig().rules.map { it.raw.trim() })
    }

    @Test
    fun anImportIsMergedOrReplacedAndTheCountsAreReturned() {
        val import = """[
            {"remarks":"YouTube","outboundTag":"proxy","domain":["geosite:youtube"]},
            {"remarks":"Local","outboundTag":"direct","ip":["geoip:private"],"enabled":false},
            {"remarks":"rest","outboundTag":"direct","port":"0-65535","network":"tcp,udp"}
        ]""".parseV2rayRoutingImportOrNull()!!

        val merged = controller.importRoutingRows(7, import, ClipboardImportMode.Merge)
        assertIs<TrafficConfigEditorResult.Saved>(merged.result)
        assertEquals(2, merged.added)
        assertEquals("FINAL,PROXY", lines().last())
        assertTrue(lines().contains("DOMAIN-SET,geosite:youtube,PROXY // YouTube"))

        val replaced = controller.importRoutingRows(7, import, ClipboardImportMode.Replace)
        assertEquals(2, replaced.added)
        assertEquals(
            listOf("DOMAIN-SET,geosite:youtube,PROXY // YouTube", "GEOIP,private,DIRECT,skipi-disabled // Local", "FINAL,DIRECT"),
            lines(),
        )
    }
}
