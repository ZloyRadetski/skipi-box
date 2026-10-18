// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.withShadowrocketRuleAdded
import features.config.withShadowrocketRuleLine
import features.config.withShadowrocketRulesReordered
import features.config.withoutShadowrocketRuleLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficConfigRuleEditorControllerTest {
    @Test
    fun listDocumentCommitUsesThePassedSnapshotOnLatestSameIdRow() {
        val opened = profile(21, "Opened")
        val sibling = profile(22, "Sibling")
        val latest = opened.copy(
            name = "Latest metadata",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            androidSettings = opened.androidSettings.copy(enableFakeDns = true),
            rawConfig = opened.rawConfig
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val store = InMemoryProfileStore(latest, sibling)
        val controller = TrafficConfigEditorController(store)

        // Android's list callback derives its document from the current composition's
        // config.rawConfig, then AppState applies that exact document to the latest row by ID.
        val openedRule = opened.rawConfig.analyzeShadowrocketConfig().rules.first()
        val listDocument = opened.rawConfig.withoutShadowrocketRuleLine(openedRule.lineNumber)
        val result = controller.saveRuleDocument(opened.id, listDocument)

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val saved = store.profile(opened.id)!!
        assertEquals(listDocument, saved.rawConfig)
        assertEquals("Latest metadata", saved.name)
        assertEquals("https://latest.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertTrue(saved.androidSettings.enableFakeDns)
        assertFalse(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals(sibling, store.profile(sibling.id))
    }

    @Test
    fun listDocumentHelpersKeepFinalCommentsAndOtherSectionsInAndroidPositions() {
        val opened = profile(23, "Rules")
        val raw = opened.rawConfig
        val reordered = raw.withShadowrocketRulesReordered(fromRuleIndex = 0, toRuleIndex = 1)

        assertEquals(
            listOf("DOMAIN,second.example,DIRECT", "DOMAIN-SUFFIX,first.example,PROXY,legacy-option"),
            reordered.analyzeShadowrocketConfig().rules.filterNot { it.isFinal }.map { it.raw },
        )
        assertTrue(reordered.contains("# comment stays between rule slots\nDOMAIN-SUFFIX,first.example,PROXY"))
        assertTrue(reordered.contains("FINAL,PROXY\n\n[Vendor]\nvendor-option = opened"))

        val withInsertedRule = raw.withShadowrocketRuleAdded("DOMAIN,added.example,DIRECT")
        assertEquals(
            listOf("DOMAIN-SUFFIX,first.example,PROXY,legacy-option", "DOMAIN,second.example,DIRECT", "DOMAIN,added.example,DIRECT", "FINAL,PROXY"),
            withInsertedRule.analyzeShadowrocketConfig().rules.map { it.raw },
        )

        val noRuleSection = "[General]\nipv6 = true\n\n[Vendor]\nkeep = yes\n"
        val withNewSection = noRuleSection.withShadowrocketRuleAdded("FINAL,PROXY")
        assertTrue(withNewSection.endsWith("[Rule]\nFINAL,PROXY\n"))
        assertTrue(withNewSection.contains("[Vendor]\nkeep = yes\n"))

        val store = InMemoryProfileStore(opened)
        val result = TrafficConfigEditorController(store).saveRuleDocument(opened.id, reordered)
        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(reordered, store.profile(opened.id)!!.rawConfig)
    }

    @Test
    fun addedRuleUsesLatestRawDocumentAndPreservesPolicyTextBeforeLineNormalization() {
        val opened = profile(24, "Opened")
        val latest = opened.copy(
            name = "Latest name",
            rawConfig = opened.rawConfig
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val sibling = profile(25, "Sibling")
        val store = InMemoryProfileStore(opened, sibling)
        store.replace(latest)
        val controller = TrafficConfigEditorController(store)
        val draft = TrafficConfigRuleDraft(
            lineNumber = null,
            isFinal = false,
            type = "DOMAIN-SUFFIX",
            value = "  added.example  ",
            policy = "  Selected policy  ",
        )

        val result = controller.saveRuleDraft(opened.id, draft)

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val saved = store.profile(opened.id)!!
        val rules = saved.rawConfig.analyzeShadowrocketConfig().rules
        // The editor does not trim policy; the line helper trims outer whitespace from the whole rule.
        assertEquals("DOMAIN-SUFFIX,added.example,  Selected policy", rules[2].raw)
        assertEquals("FINAL,PROXY", rules.last().raw)
        assertTrue(saved.rawConfig.contains("server = latest.example:8443"))
        assertTrue(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals("Latest name", saved.name)
        assertEquals(sibling, store.profile(sibling.id))
    }

    @Test
    fun editedRuleReplacesItsOriginalLineOnLatestRawAndDropsParsedOption() {
        val opened = profile(26, "Opened")
        val initialRule = opened.rawConfig.analyzeShadowrocketConfig().rules.first()
        val latest = opened.copy(
            name = "Latest while editor open",
            rawConfig = opened.rawConfig
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val store = InMemoryProfileStore(latest)
        val controller = TrafficConfigEditorController(store)
        val draft = TrafficConfigRuleDraft(
            lineNumber = initialRule.lineNumber,
            isFinal = false,
            type = "IP-CIDR",
            value = "  192.0.2.0/24  ",
            policy = "DIRECT",
        )
        val expected = latest.rawConfig.withShadowrocketRuleLine(
            initialRule.lineNumber,
            "IP-CIDR,192.0.2.0/24,DIRECT",
        )

        val result = controller.saveRuleDraft(opened.id, draft)

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val saved = store.profile(opened.id)!!
        assertEquals(expected, saved.rawConfig)
        assertTrue(saved.rawConfig.contains("server = latest.example:8443"))
        assertTrue(saved.rawConfig.contains("vendor-option = latest"))
        assertFalse(saved.rawConfig.contains("legacy-option"))
        assertEquals("Latest while editor open", saved.name)
    }

    @Test
    fun finalRuleIgnoresBlankValueAndSavesBeforeBackNavigation() {
        val opened = profile(27, "Final")
        val finalRule = opened.rawConfig.analyzeShadowrocketConfig().rules.first { it.isFinal }
        val store = InMemoryProfileStore(opened)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()

        val result = controller.saveRuleDraftThenNavigate(
            profileId = opened.id,
            draft = TrafficConfigRuleDraft(
                lineNumber = finalRule.lineNumber,
                isFinal = true,
                type = "FINAL",
                value = "",
                policy = "  DIRECT  ",
            ),
            onNavigate = {
                assertTrue(store.profile(opened.id)!!.rawConfig.contains("FINAL,  DIRECT"))
                events += "back"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(listOf("back"), events)
    }

    @Test
    fun blankPolicyOrNonFinalValueSkipsStoreButStillNavigatesBack() {
        val opened = profile(28, "Blank draft")
        val store = InMemoryProfileStore(opened)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()

        val blankPolicy = controller.saveRuleDraftThenNavigate(
            profileId = opened.id,
            draft = TrafficConfigRuleDraft(null, false, "DOMAIN", "value.example", "  "),
            onNavigate = { events += "blank-policy-back" },
        )
        val blankValue = controller.saveRuleDraftThenNavigate(
            profileId = opened.id,
            draft = TrafficConfigRuleDraft(null, false, "DOMAIN", "  ", "PROXY"),
            onNavigate = { events += "blank-value-back" },
        )

        assertIs<TrafficConfigEditorResult.Skipped>(blankPolicy)
        assertIs<TrafficConfigEditorResult.Skipped>(blankValue)
        assertEquals(0, store.updateCalls)
        assertEquals(opened, store.profile(opened.id))
        assertEquals(listOf("blank-policy-back", "blank-value-back"), events)
    }

    @Test
    fun missingProfileDoesNotResurrectButRuleBackStillNavigates() {
        val store = InMemoryProfileStore()
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()

        val result = controller.saveRuleDraftThenNavigate(
            profileId = 29,
            draft = TrafficConfigRuleDraft(null, false, "DOMAIN", "missing.example", "PROXY"),
            onNavigate = { events += "back" },
        )

        assertIs<TrafficConfigEditorResult.Missing>(result)
        assertTrue(store.profiles.isEmpty())
        assertEquals(listOf("back"), events)
    }

    @Test
    fun ruleSaveFailureDoesNotNavigateWithOrWithoutDesktopHandler() {
        val opened = profile(30, "Failure")
        val failure = IllegalStateException("disk write failed")
        val store = FailingProfileStore(failure)
        val draft = TrafficConfigRuleDraft(null, false, "DOMAIN", "rule.example", "PROXY")
        var navigated = false

        val thrown = assertFailsWith<IllegalStateException> {
            TrafficConfigEditorController(store).saveRuleDraftThenNavigate(opened.id, draft) { navigated = true }
        }
        assertSame(failure, thrown)
        assertFalse(navigated)

        var reported: Exception? = null
        val result = TrafficConfigEditorController(store, onSaveFailure = { reported = it })
            .saveRuleDraftThenNavigate(opened.id, draft) { navigated = true }
        val failed = assertIs<TrafficConfigEditorResult.Failed>(result)
        assertSame(failure, failed.cause)
        assertSame(failure, reported)
        assertFalse(navigated)
    }

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        sourceUrl = "https://opened.example/profile.conf",
        updateLocked = false,
        rawConfig = """# external comment
[General]
ipv6 = true

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

    private class InMemoryProfileStore(vararg initialProfiles: TrafficConfigState) : TrafficConfigEditorProfileStore {
        val profiles = linkedMapOf(*initialProfiles.map { it.id to it }.toTypedArray())
        var updateCalls = 0

        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean {
            updateCalls += 1
            val current = profiles[profileId] ?: return false
            profiles[profileId] = transform(current)
            return true
        }

        fun replace(profile: TrafficConfigState) {
            profiles[profile.id] = profile
        }

        fun profile(id: Int): TrafficConfigState? = profiles[id]
    }

    private class FailingProfileStore(private val failure: Exception) : TrafficConfigEditorProfileStore {
        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean = throw failure
    }
}
