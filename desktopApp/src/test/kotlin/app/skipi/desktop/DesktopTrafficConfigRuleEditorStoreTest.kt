// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.app.config.TrafficConfigRuleDraft
import features.config.ConfigProfile
import features.config.analyzeShadowrocketConfig
import features.config.withoutShadowrocketRuleLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopTrafficConfigRuleEditorStoreTest {
    @Test
    fun ruleEditorCommitsAgainstLatestProfileAndPreservesDesktopLibraryEnvelope() {
        val active = ConfigProfile(7, "Active", "[General]\nloglevel = warning\n")
        val target = profile(9, "Target", "vendor-option = opened")
        val sibling = ConfigProfile(13, "Sibling", "[General]\nloglevel = info\n")
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, target, sibling),
            nextConfigId = 18,
        )
        var latest = initial
        var persisted = initial
        val events = mutableListOf<String>()
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { library ->
                events += "persist"
                persisted = library
                Result.success(Unit)
            },
            publishLibrary = { library ->
                events += "publish"
                latest = library
            },
        )
        val store = DesktopTrafficConfigEditorProfileStore(
            repository = repository,
            resourceUserAgentFallback = "Skipi/9.8.7/Desktop",
        )
        val controller = TrafficConfigEditorController(store)

        // A different edit lands while the rule screen is open. The controller must use the
        // repository's latest same-ID row, preserving its document changes and library envelope.
        val latestTarget = target.copy(
            content = target.content
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val addedNeighbor = ConfigProfile(30, "Added while editing", "[General]\nloglevel = debug\n")
        val current = initial.copy(
            selectedConfigId = sibling.id,
            configs = listOf(active, latestTarget, sibling, addedNeighbor),
            nextConfigId = 41,
        )
        latest = current
        persisted = current

        val result = controller.saveRuleDraft(
            profileId = target.id,
            draft = TrafficConfigRuleDraft(
                lineNumber = null,
                isFinal = false,
                type = "DOMAIN-SUFFIX",
                value = "  added.example  ",
                policy = "PROXY",
            ),
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(listOf("persist", "publish"), events)
        assertEquals(current.selectedConfigId, latest.selectedConfigId)
        assertEquals(current.nextConfigId, latest.nextConfigId)
        assertEquals(current.configs.map(ConfigProfile::id), latest.configs.map(ConfigProfile::id))
        assertEquals(active, latest.configs.single { it.id == active.id })
        assertEquals(sibling, latest.configs.single { it.id == sibling.id })
        assertEquals(addedNeighbor, latest.configs.single { it.id == addedNeighbor.id })

        val saved = latest.configs.single { it.id == target.id }
        assertEquals(target.name, saved.name)
        assertEquals(target.sourceUrl, saved.sourceUrl)
        assertEquals(target.updateLocked, saved.updateLocked)
        assertEquals(target.lastUpdatedAtMillis, saved.lastUpdatedAtMillis)
        assertTrue(saved.content.contains("server = latest.example:8443"))
        assertTrue(saved.content.contains("vendor-option = latest"))
        val rules = saved.content.analyzeShadowrocketConfig().rules
        assertEquals("DOMAIN-SUFFIX,added.example,PROXY", rules[rules.lastIndex - 1].raw)
        assertEquals("FINAL,PROXY", rules.last().raw)
        assertEquals(latest, persisted)
    }

    @Test
    fun listSnapshotSaveUsesPassedDocumentWhileDesktopUpdaterPreservesCurrentEnvelopeAndSiblings() {
        val active = ConfigProfile(3, "Active", "[General]\nloglevel = warning\n")
        val target = profile(8, "Target", "vendor-option = opened")
        val sibling = ConfigProfile(12, "Sibling", "[General]\nloglevel = info\n")
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, target, sibling),
            nextConfigId = 15,
        )
        var latest = initial
        var persisted = initial
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { persisted = it; Result.success(Unit) },
            publishLibrary = { latest = it },
        )
        val controller = TrafficConfigEditorController(
            DesktopTrafficConfigEditorProfileStore(repository, "Skipi/9.8.7/Desktop"),
        )

        val latestTarget = target.copy(
            content = target.content.replace("vendor-option = opened", "vendor-option = latest"),
        )
        val addedNeighbor = ConfigProfile(22, "Added neighbor", "[General]\nloglevel = debug\n")
        val current = initial.copy(
            selectedConfigId = sibling.id,
            configs = listOf(active, latestTarget, sibling, addedNeighbor),
            nextConfigId = 29,
        )
        latest = current
        persisted = current

        val targetRuleLine = target.content.analyzeShadowrocketConfig().rules.first().lineNumber
        val capturedListDocument = target.content.withoutShadowrocketRuleLine(targetRuleLine)
        val result = controller.saveRuleDocument(target.id, capturedListDocument)

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(current.selectedConfigId, latest.selectedConfigId)
        assertEquals(current.nextConfigId, latest.nextConfigId)
        assertEquals(current.configs.map(ConfigProfile::id), latest.configs.map(ConfigProfile::id))
        assertEquals(active, latest.configs.single { it.id == active.id })
        assertEquals(sibling, latest.configs.single { it.id == sibling.id })
        assertEquals(addedNeighbor, latest.configs.single { it.id == addedNeighbor.id })
        val saved = latest.configs.single { it.id == target.id }
        assertEquals(target.name, saved.name)
        assertEquals(target.sourceUrl, saved.sourceUrl)
        assertEquals(target.updateLocked, saved.updateLocked)
        assertEquals(target.lastUpdatedAtMillis, saved.lastUpdatedAtMillis)
        assertTrue(saved.content.contains("vendor-option = opened"))
        assertFalse(saved.content.contains("vendor-option = latest"))
        assertEquals(
            listOf("DOMAIN,second.example,DIRECT", "FINAL,PROXY"),
            saved.content.analyzeShadowrocketConfig().rules.map { it.raw },
        )
        assertEquals(latest, persisted)
    }

    @Test
    fun failedRuleCommitReportsFailureAndKeepsTheEditorOpen() {
        val target = profile(5, "Target", "vendor-option = opened")
        val initial = DesktopConfigLibrary(
            selectedConfigId = target.id,
            configs = listOf(target),
            nextConfigId = 6,
        )
        var latest = initial
        var publishCalls = 0
        val failure = IllegalStateException("disk write failed")
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { Result.failure(failure) },
            publishLibrary = {
                publishCalls += 1
                latest = it
            },
        )
        var reported: Exception? = null
        val controller = TrafficConfigEditorController(
            store = DesktopTrafficConfigEditorProfileStore(repository, "Skipi/9.8.7/Desktop"),
            onSaveFailure = { reported = it },
        )
        var navigated = false

        val result = controller.saveRuleDraftThenNavigate(
            profileId = target.id,
            draft = TrafficConfigRuleDraft(null, false, "DOMAIN", "rule.example", "PROXY"),
            onNavigate = { navigated = true },
        )

        val failed = assertIs<TrafficConfigEditorResult.Failed>(result)
        assertSame(failure, failed.cause)
        assertSame(failure, reported)
        assertFalse(navigated)
        assertEquals(0, publishCalls)
        assertEquals(initial, latest)
    }

    private fun profile(id: Int, name: String, vendorLine: String): ConfigProfile = ConfigProfile(
        id = id,
        name = name,
        sourceUrl = "https://opened.example/profile.conf",
        updateLocked = true,
        lastUpdatedAtMillis = 456L,
        content = """[General]
loglevel = warning

[Proxy]
server = opened.example:443

[Rule]
DOMAIN-SUFFIX,first.example,PROXY,legacy-option
# comment stays between rule slots
DOMAIN,second.example,DIRECT
FINAL,PROXY

[Vendor]
$vendorLine
""",
    )
}
