// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Source-derived oracle for the current Android rules list and rule-editor update lambdas. */
class TrafficConfigRulesAndroidOracleTest {
    @Test
    fun listDeleteCommitsTheCompositionSnapshotToTheLatestProfileRow() {
        val opened = profile(41, "Opened")
        val sibling = profile(42, "Sibling")
        val latest = opened.copy(
            name = "Latest metadata",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 567L,
            androidSettings = opened.androidSettings.copy(enableFakeDns = true),
            rawConfig = opened.rawConfig
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val before = appState(latest, sibling)

        // TrafficConfigRulesPage's onDelete uses config.rawConfig from the current
        // composition; updateRaw then applies that document to the latest row by ID.
        val displayedRaw = opened.rawConfig
        val firstRuleLine = displayedRaw.analyzeShadowrocketConfig().rules.first().lineNumber
        val listDocument = displayedRaw.withoutShadowrocketRuleLine(firstRuleLine)
        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = listDocument)
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        assertEquals("Latest metadata", saved.name)
        assertEquals("https://latest.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertEquals(567L, saved.lastUpdatedAtMillis)
        assertTrue(saved.androidSettings.enableFakeDns)
        assertFalse(saved.rawConfig.contains("server = latest.example:8443"))
        assertTrue(saved.rawConfig.contains("vendor-option = opened"))
        assertFalse(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals(
            listOf("DOMAIN,second.example,DIRECT", "FINAL,PROXY"),
            saved.rawConfig.analyzeShadowrocketConfig().rules.map(ShadowrocketRule::raw),
        )
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
        assertEquals(sibling.id, after.activeTrafficConfigId)
        assertEquals(99, after.nextTrafficConfigId)
    }

    @Test
    fun listReorderUsesLocalRawAndKeepsCommentsAtTheirOriginalLineSlots() {
        val opened = profile(51, "Reorder")
        val sibling = profile(52, "Sibling")
        val latest = opened.copy(
            rawConfig = opened.rawConfig.replace("vendor-option = opened", "vendor-option = latest"),
        )
        val before = appState(latest, sibling)

        // TrafficConfigRulesPage.onMove is deliberately based on localRawConfig.
        val localRawConfig = opened.rawConfig
        val reorderedRaw = localRawConfig.withShadowrocketRulesReordered(fromRuleIndex = 0, toRuleIndex = 1)
        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = reorderedRaw)
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        assertEquals(
            listOf("DOMAIN,second.example,DIRECT", "DOMAIN-SUFFIX,first.example,PROXY,legacy-option", "FINAL,PROXY"),
            saved.rawConfig.analyzeShadowrocketConfig().rules.map(ShadowrocketRule::raw),
        )
        assertTrue(saved.rawConfig.contains("# comment stays between rule slots\nDOMAIN-SUFFIX,first.example,PROXY,legacy-option"))
        assertTrue(saved.rawConfig.contains("vendor-option = opened"))
        assertFalse(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals("Reorder", saved.name)
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
    }

    @Test
    fun absentFinalRuleIsAddedFromListSnapshotAndCreatesRuleSectionAtEndWhenMissing() {
        val opened = profileWithoutRules(61, "Opened")
        val latest = opened.copy(
            name = "Latest metadata",
            rawConfig = opened.rawConfig.replace("vendor-option = opened", "vendor-option = latest"),
        )
        val sibling = profile(62, "Sibling")
        val before = appState(latest, sibling)

        // TrafficConfigRulesPage.onEditFinal uses config.rawConfig when no FINAL exists.
        val displayedRaw = opened.rawConfig
        val listDocument = displayedRaw.withShadowrocketRuleAdded("FINAL,PROXY")
        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = listDocument)
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        assertTrue(saved.rawConfig.contains("[Vendor]\nvendor-option = opened\n"))
        assertTrue(saved.rawConfig.indexOf("[Vendor]") < saved.rawConfig.indexOf("[Rule]"))
        assertEquals(listOf("FINAL,PROXY"), saved.rawConfig.analyzeShadowrocketConfig().rules.map(ShadowrocketRule::raw))
        assertFalse(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals("Latest metadata", saved.name)
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
    }

    @Test
    fun editorAddUsesLatestRawWhileEditUsesCapturedLineNumberOnLatestRawAndDropsOption() {
        val opened = profile(71, "Opened")
        val originalRule = opened.rawConfig.analyzeShadowrocketConfig().rules.first()
        val latest = opened.copy(
            name = "Latest metadata",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            androidSettings = opened.androidSettings.copy(enableFakeDns = true),
            rawConfig = opened.rawConfig
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val sibling = profile(72, "Sibling")
        val before = appState(latest, sibling)

        // TrafficConfigRuleEditorPage.save transforms current.rawConfig in the latest row.
        val editLine = "IP-CIDR,192.0.2.0/24,DIRECT"
        val afterEdit = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = current.rawConfig.withShadowrocketRuleLine(originalRule.lineNumber, editLine))
        }
        val edited = afterEdit.trafficConfigs.single { it.id == opened.id }
        assertTrue(edited.rawConfig.contains(editLine))
        assertFalse(edited.rawConfig.contains("legacy-option"))
        assertTrue(edited.rawConfig.contains("server = latest.example:8443"))
        assertTrue(edited.rawConfig.contains("vendor-option = latest"))
        assertEquals("Latest metadata", edited.name)
        assertTrue(edited.updateLocked)
        assertTrue(edited.androidSettings.enableFakeDns)
        assertEquals(sibling, afterEdit.trafficConfigs.single { it.id == sibling.id })

        val addedLine = "DOMAIN-SUFFIX,added.example,  Selected policy"
        val afterAdd = afterEdit.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = current.rawConfig.withShadowrocketRuleAdded(addedLine))
        }
        val added = afterAdd.trafficConfigs.single { it.id == opened.id }
        val rules = added.rawConfig.analyzeShadowrocketConfig().rules
        assertEquals(addedLine, rules[rules.lastIndex - 1].raw)
        assertEquals("FINAL,PROXY", rules.last().raw)
        assertTrue(added.rawConfig.contains("vendor-option = latest"))
    }

    @Test
    fun blankRuleDraftSkipsTheAndroidUpdaterButBackStillLeaves() {
        val opened = profile(81, "Blank")
        val before = appState(opened)
        var state = before
        var updateCalls = 0
        val events = mutableListOf<String>()

        fun saveLikeAndroid(selectedType: String, value: String, policy: String, isFinal: Boolean) {
            if (policy.isBlank() || (!isFinal && value.isBlank())) return
            val line = if (isFinal) "FINAL,$policy" else "$selectedType,${value.trim()},$policy"
            updateCalls += 1
            state = state.withUpdatedTrafficConfigProfile(opened.id) { current ->
                current.copy(rawConfig = current.rawConfig.withShadowrocketRuleAdded(line))
            }
        }

        saveLikeAndroid(selectedType = "DOMAIN", value = "value.example", policy = "  ", isFinal = false)
        events += "pop"

        assertEquals(0, updateCalls)
        assertEquals(before, state)
        assertEquals(listOf("pop"), events)
    }

    @Test
    fun missingProfileUpdateDoesNotResurrectItAndBackStillLeaves() {
        val before = appState(profile(91, "Other"))
        val after = before.withUpdatedTrafficConfigProfile(92) { current ->
            current.copy(rawConfig = current.rawConfig.withShadowrocketRuleAdded("DOMAIN,missing.example,PROXY"))
        }
        val events = mutableListOf<String>()
        events += "pop"

        assertEquals(before, after)
        assertEquals(listOf(91), after.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(listOf("pop"), events)
    }

    private fun appState(vararg profiles: TrafficConfigState): AppState = AppState(
        trafficConfigs = profiles.toList(),
        activeTrafficConfigId = profiles.last().id,
        nextTrafficConfigId = 99,
    )

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        sourceUrl = "https://opened.example/profile.conf",
        updateLocked = false,
        lastUpdatedAtMillis = 123L,
        rawConfig = """[General]
loglevel = warning

[Proxy]
server = opened.example:443

[Rule]
DOMAIN-SUFFIX,first.example,PROXY,legacy-option
# comment stays between rule slots
DOMAIN,second.example,DIRECT
FINAL,PROXY

[Vendor]
vendor-option = opened
""",
    )

    private fun profileWithoutRules(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        sourceUrl = "https://opened.example/profile.conf",
        rawConfig = """[General]
loglevel = warning

[Vendor]
vendor-option = opened
""",
    )
}
