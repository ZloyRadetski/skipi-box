// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_add
import app.skipi.ui.resources.configs_copy_name
import app.skipi.ui.resources.configs_duplicate
import app.skipi.ui.resources.configs_global_proxy_groups
import app.skipi.ui.resources.configs_profiles
import app.skipi.ui.resources.configs_proxy_groups_title
import app.skipi.ui.resources.configs_preserved_unsupported
import app.skipi.ui.resources.configs_resources
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.deletion_confirmation_delete_proxy_server
import app.skipi.ui.resources.proxy_editor_discard
import app.skipi.ui.resources.proxy_editor_discard_changes_title
import app.skipi.ui.resources.proxy_editor_strategy_group_title
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.withSkipiSettingsInRawConfig
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import engine.network.NetworkDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TestSceneWidth = 1500
private const val TestSceneHeight = 1600
private const val TestResourceUserAgent = "Desktop config collection integration test"
private const val TargetProfileGroupName = "Inactive profile only group"
private const val ActiveProfileGroupName = "Active profile only group"
private const val StandaloneGlobalGroupName = "Standalone global group"
private const val ProfileOwnedGlobalGroupName = "Profile-owned global group"
private const val NonStrategyAutoGroupName = "Non-strategy auto row"
private const val ManualStrategyGroupName = "Manual strategy group"

class DesktopTrafficConfigCollectionScreenIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun sourcedGroupOpensItsInactiveProfileAndBackReturnsToCatalogWithoutWrites() = runBlocking {
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val profilesTitle = getString(Res.string.configs_profiles)
        val proxyServers = emptyList<CatalogProxyServerRecord>()
        withDesktopScreen(proxyServers) { screen ->
            assertNotNull(awaitText(screen.scene, TargetProfileGroupName), "The collection should include groups from inactive profiles")
            assertNotNull(awaitText(screen.scene, ActiveProfileGroupName), "The collection fixture should include the active profile's group too")

            clickTextAction(screen.scene, TargetProfileGroupName)
            assertNotNull(awaitText(screen.scene, groupsTitle))
            assertNotNull(awaitText(screen.scene, TargetProfileGroupName), "The source row should open that profile's groups")
            assertFalse(
                hasVisibleText(screen.scene, ActiveProfileGroupName),
                "Opening the inactive profile's source row must not substitute the active profile's groups",
            )

            clickTopBarBack(screen.scene)
            assertNotNull(awaitText(screen.scene, profilesTitle), "Back should return directly to the collection catalog")
            assertNotNull(awaitText(screen.scene, TargetProfileGroupName), "The collection catalog should be restored after Back")
            assertNotNull(awaitText(screen.scene, ActiveProfileGroupName))

            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Opening and leaving a source group must not write profile data")
            assertEquals(screen.initialLibrary.selectedConfigId, screen.persistedLibrary().selectedConfigId)
            assertEquals(0, screen.libraryTransformCount(), "Catalog navigation must not invoke the profile persistence callback")
            assertEquals(proxyServers, screen.proxyServers, "Viewing source groups must not mutate the proxy catalog input")
        }
    }

    @Test
    fun globalRowsKeepAndroidEligibilityAndOrderAndNonStrategyClickIsANoop() = runBlocking {
        val profilesTitle = getString(Res.string.configs_profiles)
        val proxyServers = listOf(
            catalogServer(
                id = 40,
                sourceSubscriptionId = DefaultAutoBalancerGroupId,
                server = StrategyGroup(remarks = StandaloneGlobalGroupName),
            ),
            catalogServer(
                id = 41,
                sourceSubscriptionId = DefaultAutoBalancerGroupId,
                server = StrategyGroup(
                    remarks = ProfileOwnedGlobalGroupName,
                    sourceTrafficConfigId = 7,
                    sourcePolicyGroupName = "Inactive profile policy",
                ),
            ),
            catalogServer(
                id = 42,
                sourceSubscriptionId = DefaultAutoBalancerGroupId,
                server = VLESS(
                    remarks = NonStrategyAutoGroupName,
                    id = "auto-row-user",
                    server = "auto-row.example",
                    port = "443",
                    encryption = "none",
                ),
            ),
            catalogServer(
                id = 43,
                sourceSubscriptionId = null,
                server = StrategyGroup(remarks = ManualStrategyGroupName),
            ),
        )

        withDesktopScreen(proxyServers) { screen ->
            val standalone = assertNotNull(awaitText(screen.scene, StandaloneGlobalGroupName))
            val nonStrategy = assertNotNull(awaitText(screen.scene, NonStrategyAutoGroupName))
            assertTrue(
                standalone.boundsInRoot.top < nonStrategy.boundsInRoot.top,
                "Eligible global rows should preserve proxy catalog order while hidden rows are skipped",
            )
            assertFalse(hasVisibleText(screen.scene, ProfileOwnedGlobalGroupName), "Profile-owned strategy groups are not global rows")
            assertFalse(hasVisibleText(screen.scene, ManualStrategyGroupName), "Manual strategy groups are not global rows")

            clickTextAction(screen.scene, NonStrategyAutoGroupName)

            assertNotNull(awaitText(screen.scene, StandaloneGlobalGroupName), "A non-strategy row click should leave the collection visible")
            assertNotNull(awaitText(screen.scene, NonStrategyAutoGroupName), "A non-strategy auto-balancer row should be a click no-op")
            assertNotNull(awaitText(screen.scene, profilesTitle), "The click should not enter an editor")
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "A non-strategy row click must not write profile data")
            assertEquals(0, screen.libraryTransformCount(), "A non-strategy row click must not invoke the profile persistence callback")
            assertEquals(proxyServers, screen.proxyServers, "A non-strategy row click must not mutate proxy catalog records")
        }
    }

    @Test
    fun addingGlobalGroupKeepsDraftUntilSaveAndEmitsAutoBalancerResult() = runBlocking {
        val globalGroupsTitle = getString(Res.string.configs_global_proxy_groups)
        val addLabel = getString(Res.string.configs_add)
        val saveLabel = getString(Res.string.common_save)
        val newName = "New global strategy group"
        val proxyServers = emptyList<CatalogProxyServerRecord>()

        withDesktopScreen(
            proxyServers = proxyServers,
            scriptedApplyOutcome = ProxyServerEditApplyOutcome.Saved(
                serverId = 91,
                wasExistingAtCommit = false,
                selectedGroupId = DefaultAutoBalancerGroupId,
            ),
        ) { screen ->
            assertNotNull(awaitText(screen.scene, globalGroupsTitle))
            clickContentDescriptionAction(screen.scene, addLabel)
            assertEquals("", awaitEditableText(screen.scene, ""), "Adding a global group should start with a blank strategy draft")
            assertEquals(0, screen.applyRequests().size, "Opening a new group editor must not apply its blank draft")

            setFirstEditableText(screen.scene, newName)
            assertEquals(newName, awaitEditableText(screen.scene, newName))
            assertEquals(0, screen.applyRequests().size, "Editing a new group must remain a draft until Save")
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(proxyServers, screen.proxyServers)

            clickContentDescriptionAction(screen.scene, saveLabel)

            val request = screen.applyRequests().single()
            assertEquals(null, request.serverId)
            assertEquals(DefaultAutoBalancerGroupId, request.groupId)
            assertEquals(DefaultAutoBalancerGroupId, request.returnGroupId)
            assertFalse(request.deleted)
            assertEquals(
                StrategyGroup(remarks = newName, probeUrl = NetworkDefaults.CONNECTIVITY_CHECK_URL),
                request.server,
                "The shared editor fills a blank probe URL from its common connectivity default on first composition",
            )
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Global group apply must not write traffic profiles")
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(proxyServers, screen.proxyServers)
        }
    }

    @Test
    fun duplicatingGlobalGroupCreatesLocalizedDetachedDraftAndPreservesItsFields() = runBlocking {
        val duplicateLabel = getString(Res.string.configs_duplicate)
        val saveLabel = getString(Res.string.common_save)
        val copyNamePattern = getString(Res.string.configs_copy_name)
        val originalName = "Global policy group"
        val localizedCopyName = String.format(Locale.getDefault(), copyNamePattern, originalName)
        val editedCopyName = "Edited duplicate draft"
        val originalGroup = StrategyGroup(
            remarks = originalName,
            strategy = StrategyGroupConstants.TYPE_RANDOM,
            subscriptionGroupId = 23,
            filter = "region:west",
            proxyServerIds = listOf(12, 18),
            selectedMemberId = 18,
            displayMode = "never",
            showInAutoBalancerList = false,
            sourceTrafficConfigId = null,
            sourcePolicyGroupName = "preserved global policy",
            probeInterval = "30s",
            probeUrl = "https://probe.example.test/check",
            enableBurstProbe = true,
            tolerance = "125ms",
            probeTimeout = "9s",
        )
        val originalSnapshot = originalGroup.copy(proxyServerIds = originalGroup.proxyServerIds.toList())
        val proxyServers = listOf(
            catalogServer(40, DefaultAutoBalancerGroupId, originalGroup),
        )

        withDesktopScreen(
            proxyServers = proxyServers,
            scriptedApplyOutcome = ProxyServerEditApplyOutcome.Saved(
                serverId = 92,
                wasExistingAtCommit = false,
                selectedGroupId = DefaultAutoBalancerGroupId,
            ),
        ) { screen ->
            clickRowOnLongClickAction(screen.scene, originalName)
            clickTextAction(screen.scene, duplicateLabel)
            assertEquals(localizedCopyName, awaitEditableText(screen.scene, localizedCopyName))
            assertEquals(0, screen.applyRequests().size, "Duplicating should open a draft without applying it")
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40), "Opening a duplicate must not mutate the original group")

            setFirstEditableText(screen.scene, editedCopyName)
            assertEquals(editedCopyName, awaitEditableText(screen.scene, editedCopyName))
            assertEquals(0, screen.applyRequests().size, "Editing a duplicate should remain local until Save")
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40), "Editing a duplicate must leave the source row untouched")

            clickContentDescriptionAction(screen.scene, saveLabel)

            val request = screen.applyRequests().single()
            assertEquals(null, request.serverId, "A duplicate must be submitted as a new catalog record")
            assertEquals(DefaultAutoBalancerGroupId, request.groupId)
            assertEquals(DefaultAutoBalancerGroupId, request.returnGroupId)
            assertFalse(request.deleted)
            assertEquals(originalSnapshot.copy(remarks = editedCopyName), request.server)
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40))
        }
    }

    @Test
    fun nonStrategyAutoBalancerContextActionsLeaveCatalogAndDoNotApply() = runBlocking {
        val editLabel = getString(Res.string.common_edit)
        val duplicateLabel = getString(Res.string.configs_duplicate)
        val profilesTitle = getString(Res.string.configs_profiles)
        val rowName = "Non-strategy auto row context actions"
        val proxyServers = listOf(
            catalogServer(
                id = 42,
                sourceSubscriptionId = DefaultAutoBalancerGroupId,
                server = VLESS(
                    remarks = rowName,
                    id = "context-row-user",
                    server = "context-row.example",
                    port = "443",
                    encryption = "none",
                ),
            ),
        )

        withDesktopScreen(proxyServers) { screen ->
            assertNotNull(awaitText(screen.scene, rowName), "The VLESS auto-balancer row should be visible in the catalog")

            clickRowOnLongClickAction(screen.scene, rowName)
            clickTextAction(screen.scene, editLabel)
            assertNotNull(awaitText(screen.scene, rowName), "Edit on a non-strategy row should leave the catalog visible")
            assertNotNull(awaitText(screen.scene, profilesTitle), "Edit on a non-strategy row should not open the group editor")
            assertEquals(emptyList(), screen.applyRequests(), "Edit on a non-strategy row must not submit an apply result")
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(proxyServers, screen.proxyServers)

            clickRowOnLongClickAction(screen.scene, rowName)
            clickTextAction(screen.scene, duplicateLabel)
            assertNotNull(awaitText(screen.scene, rowName), "Duplicate on a non-strategy row should leave the catalog visible")
            assertNotNull(awaitText(screen.scene, profilesTitle), "Duplicate on a non-strategy row should not open a group editor")
            assertEquals(emptyList(), screen.applyRequests(), "Duplicate on a non-strategy row must not submit an apply result")
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(proxyServers, screen.proxyServers, "Context actions must leave the VLESS catalog record unchanged")
        }
    }

    @Test
    fun duplicatingBlankNamedGlobalGroupUsesProtocolFallbackForItsSavedName() = runBlocking {
        val duplicateLabel = getString(Res.string.configs_duplicate)
        val saveLabel = getString(Res.string.common_save)
        val copyNamePattern = getString(Res.string.configs_copy_name)
        val originalGroup = StrategyGroup()
        val originalSnapshot = originalGroup.copy(proxyServerIds = originalGroup.proxyServerIds.toList())
        val rowName = originalGroup.getInfo().protocol
        val localizedCopyName = String.format(Locale.getDefault(), copyNamePattern, rowName)
        val proxyServers = listOf(catalogServer(44, DefaultAutoBalancerGroupId, originalGroup))

        withDesktopScreen(
            proxyServers = proxyServers,
            scriptedApplyOutcome = ProxyServerEditApplyOutcome.Saved(
                serverId = 94,
                wasExistingAtCommit = false,
                selectedGroupId = DefaultAutoBalancerGroupId,
            ),
        ) { screen ->
            assertNotNull(awaitText(screen.scene, rowName), "A blank strategy-group name should fall back to its protocol in the catalog")
            clickRowOnLongClickAction(screen.scene, rowName)
            clickTextAction(screen.scene, duplicateLabel)

            assertEquals(localizedCopyName, awaitEditableText(screen.scene, localizedCopyName))
            assertEquals(0, screen.applyRequests().size, "Opening a duplicate draft must not apply it")
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 44))

            clickContentDescriptionAction(screen.scene, saveLabel)

            val request = screen.applyRequests().single()
            assertEquals(null, request.serverId, "Saving a duplicate should create a new catalog record")
            assertEquals(DefaultAutoBalancerGroupId, request.groupId)
            assertEquals(DefaultAutoBalancerGroupId, request.returnGroupId)
            assertFalse(request.deleted)
            assertEquals(
                originalSnapshot.copy(
                    remarks = localizedCopyName,
                    probeUrl = NetworkDefaults.CONNECTIVITY_CHECK_URL,
                ),
                request.server,
                "A blank StrategyGroup should use its protocol in the localized copy name and retain the shared editor's probe default",
            )
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 44))
        }
    }

    @Test
    fun failedGlobalGroupEditShowsAdapterReasonAndKeepsPersistedDataUnchanged() = runBlocking {
        val saveLabel = getString(Res.string.common_save)
        val originalName = "Global group with rejected save"
        val attemptedName = "Rejected edited global group"
        val failureReason = "scripted global-group rejection reason 9f2a"
        val originalGroup = StrategyGroup(
            remarks = originalName,
            strategy = StrategyGroupConstants.TYPE_ROUND_ROBIN,
            probeUrl = "https://failure.example.test/check",
        )
        val originalSnapshot = originalGroup.copy(proxyServerIds = originalGroup.proxyServerIds.toList())
        val proxyServers = listOf(catalogServer(45, DefaultAutoBalancerGroupId, originalGroup))

        withDesktopScreen(
            proxyServers = proxyServers,
            scriptedApplyOutcome = ProxyServerEditApplyOutcome.Failed(failureReason),
            viewportWidth = 390,
            viewportHeight = 844,
        ) { screen ->
            clickTextAction(screen.scene, originalName)
            assertEquals(originalName, awaitEditableText(screen.scene, originalName))
            setFirstEditableText(screen.scene, attemptedName)
            assertEquals(0, screen.applyRequests().size, "Editing should remain a draft until Save")

            clickContentDescriptionAction(screen.scene, saveLabel)

            val request = screen.applyRequests().single()
            assertEquals(45, request.serverId)
            assertEquals(DefaultAutoBalancerGroupId, request.groupId)
            assertEquals(DefaultAutoBalancerGroupId, request.returnGroupId)
            assertFalse(request.deleted)
            assertEquals(originalSnapshot.copy(remarks = attemptedName), request.server)
            assertNotNull(awaitText(screen.scene, originalName), "After the failed apply, the collection should be visible again")
            assertNotNull(
                awaitViewportNode(screen.scene, width = 390, height = 844) { candidate ->
                    candidate.hasTextContaining(failureReason)
                },
                "The failed apply reason should be visible inside a 390x844 viewport",
            )
            assertEquals(proxyServers, screen.proxyServers, "The failed apply must not mutate the proxy catalog input")
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 45))
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "A failed global edit must not change traffic profiles")
            assertEquals(0, screen.libraryTransformCount())
        }
    }

    @Test
    fun globalDeleteCancelDoesNotApplyAndConfirmSubmitsOnlyTheGlobalRecord() = runBlocking {
        val deleteLabel = getString(Res.string.common_delete)
        val cancelLabel = getString(Res.string.common_cancel)
        val confirmationTitle = getString(Res.string.deletion_confirmation_delete_proxy_server)
        val originalGroup = StrategyGroup(remarks = StandaloneGlobalGroupName)
        val originalSnapshot = originalGroup.copy(proxyServerIds = originalGroup.proxyServerIds.toList())
        val proxyServers = listOf(
            catalogServer(40, DefaultAutoBalancerGroupId, originalGroup),
        )

        withDesktopScreen(
            proxyServers = proxyServers,
            scriptedApplyOutcome = ProxyServerEditApplyOutcome.Deleted,
        ) { screen ->
            assertNotNull(awaitText(screen.scene, TargetProfileGroupName))
            assertFalse(
                rowHasContentDescriptionAction(screen.scene, TargetProfileGroupName, deleteLabel),
                "A profile-sourced row must not show a global catalog delete action",
            )

            clickRowContentDescriptionAction(screen.scene, StandaloneGlobalGroupName, deleteLabel)
            assertNotNull(awaitText(screen.scene, confirmationTitle))
            assertEquals(0, screen.applyRequests().size, "Opening global deletion confirmation must not apply anything")
            clickTextAction(screen.scene, cancelLabel)
            assertNotNull(awaitText(screen.scene, StandaloneGlobalGroupName), "Cancel should leave the catalog row visible")
            assertEquals(0, screen.applyRequests().size, "Cancel must not apply a delete result")
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40))

            clickRowContentDescriptionAction(screen.scene, StandaloneGlobalGroupName, deleteLabel)
            assertNotNull(awaitText(screen.scene, confirmationTitle))
            clickDialogTextAction(screen.scene, confirmationTitle, deleteLabel)

            val request = screen.applyRequests().single()
            assertEquals(40, request.serverId)
            assertEquals(DefaultAutoBalancerGroupId, request.groupId)
            assertEquals(DefaultAutoBalancerGroupId, request.returnGroupId)
            assertTrue(request.deleted)
            assertEquals(originalSnapshot, request.server)
            assertNotNull(awaitText(screen.scene, TargetProfileGroupName), "Confirm should return from the editor without deleting profile rows")
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Deleting a global row must not change config profiles")
            assertEquals(3, screen.persistedLibrary().selectedConfigId)
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40))
        }
    }

    @Test
    fun editingExistingGlobalGroupKeepsIdentityAndAppliesOnlyAfterSave() = runBlocking {
        val originalName = "Existing global group 40"
        val editedName = "Edited global group 40"
        val saveLabel = getString(Res.string.common_save)
        val originalGroup = StrategyGroup(
            remarks = originalName,
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(12, 18),
            probeUrl = "https://existing.example.test/check",
        )
        val originalSnapshot = originalGroup.copy(proxyServerIds = originalGroup.proxyServerIds.toList())
        val proxyServers = listOf(catalogServer(40, DefaultAutoBalancerGroupId, originalGroup))

        withDesktopScreen(
            proxyServers = proxyServers,
            scriptedApplyOutcome = ProxyServerEditApplyOutcome.Saved(
                serverId = 40,
                wasExistingAtCommit = true,
                selectedGroupId = DefaultAutoBalancerGroupId,
            ),
        ) { screen ->
            clickTextAction(screen.scene, originalName)
            assertEquals(originalName, awaitEditableText(screen.scene, originalName))
            assertEquals(0, screen.applyRequests().size, "Opening an existing global row should not commit anything")

            setFirstEditableText(screen.scene, editedName)
            assertEquals(editedName, awaitEditableText(screen.scene, editedName))
            assertEquals(0, screen.applyRequests().size, "An edited global draft should remain local until Save")
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40))

            clickContentDescriptionAction(screen.scene, saveLabel)

            val request = screen.applyRequests().single()
            assertEquals(40, request.serverId)
            assertEquals(DefaultAutoBalancerGroupId, request.groupId)
            assertEquals(DefaultAutoBalancerGroupId, request.returnGroupId)
            assertFalse(request.deleted)
            assertEquals(originalSnapshot.copy(remarks = editedName), request.server)
            assertEquals(proxyServers, screen.proxyServers)
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
        }
    }

    @Test
    fun discardingEditedGlobalGroupDoesNotApplyOrMutateTheOriginal() = runBlocking {
        val originalName = "Global group for discard"
        val draftName = "Uncommitted global group name"
        val discardTitle = getString(Res.string.proxy_editor_discard_changes_title)
        val discardLabel = getString(Res.string.proxy_editor_discard)
        val originalGroup = StrategyGroup(
            remarks = originalName,
            strategy = StrategyGroupConstants.TYPE_RANDOM,
            probeUrl = "https://discard.example.test/check",
        )
        val originalSnapshot = originalGroup.copy(proxyServerIds = originalGroup.proxyServerIds.toList())
        val proxyServers = listOf(catalogServer(40, DefaultAutoBalancerGroupId, originalGroup))

        withDesktopScreen(proxyServers) { screen ->
            clickTextAction(screen.scene, originalName)
            assertEquals(originalName, awaitEditableText(screen.scene, originalName))
            setFirstEditableText(screen.scene, draftName)
            assertEquals(draftName, awaitEditableText(screen.scene, draftName))
            assertEquals(0, screen.applyRequests().size)

            clickTopBarBack(screen.scene)
            assertNotNull(awaitText(screen.scene, discardTitle), "Leaving a changed global draft should request discard confirmation")
            clickDialogTextAction(screen.scene, discardTitle, discardLabel)

            assertNotNull(awaitText(screen.scene, originalName), "Discard should return to the catalog with the original row")
            assertEquals(0, screen.applyRequests().size, "Discard must not apply the edited draft")
            assertEquals(originalSnapshot, globalStrategy(screen.proxyServers, 40))
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(3, screen.persistedLibrary().selectedConfigId)
            assertEquals(0, screen.libraryTransformCount())
        }
    }

    @Test
    fun savedProfileCardShowsSourceSummaryLockDiagnosticsAndCopyMoreActions() = runBlocking {
        val profileName = "Saved profile with visible metadata"
        val sourceUrl = "https://profiles.example.test/saved.conf"
        val target = normalizedProfile(
            id = 7,
            name = profileName,
            sourceUrl = sourceUrl,
            updateLocked = true,
            content = """[Rule]
DOMAIN-SUFFIX,example.com,PROXY
IP-CIDR,10.0.0.0/8,DIRECT
FINAL,DIRECT
malformed rule row

[Proxy Group]
Manual = select, DIRECT
Probe = url-test, DIRECT, url=https://probe.example.test/ping
""",
        )
        val active = normalizedProfile(
            id = 3,
            name = "Active metadata fixture",
            sourceUrl = "",
            updateLocked = false,
            content = """[General]
loglevel = debug
""",
        )
        val duplicateLabel = getString(Res.string.configs_duplicate)

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = target,
            activeProfile = active,
        ) { screen ->
            assertNotNull(awaitText(screen.scene, profileName))
            assertNotNull(awaitText(screen.scene, sourceUrl), "The saved profile card should show its update URL")
            assertNotNull(awaitText(screen.scene, "Правила: 3 · Группы: 2"), "The card should show parser-derived rule and group counts")
            assertNotNull(awaitTextContaining(screen.scene, "Обновление заблокировано"))
            assertNotNull(awaitTextContaining(screen.scene, "Диагностика: 1"), "The card should show its parser diagnostic count")
            assertTrue(rowHasContentDescriptionAction(screen.scene, profileName, "Копировать содержимое"))
            assertTrue(rowHasContentDescriptionAction(screen.scene, profileName, "Ещё"))

            clickRowContentDescriptionAction(screen.scene, profileName, "Ещё")
            assertNotNull(awaitText(screen.scene, duplicateLabel), "The More action should open the profile context menu")

            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Reading profile metadata and opening More must not alter saved profiles")
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(3, screen.persistedLibrary().selectedConfigId)
        }
    }

    @Test
    fun savedProfileCardShowsThePreservedUnsupportedSectionNotice() = runBlocking {
        val profileName = "Profile with preserved MITM section"
        val unsupportedNotice = getString(Res.string.configs_preserved_unsupported)
        val target = normalizedProfile(
            id = 7,
            name = profileName,
            sourceUrl = "https://unsupported.example.test/profile.conf",
            updateLocked = true,
            content = """[General]
loglevel = warning

[MITM]
host = example.test
""",
        )
        val active = normalizedProfile(
            id = 3,
            name = "Active profile beside unsupported fixture",
            sourceUrl = "",
            updateLocked = false,
            content = """[General]
loglevel = debug
""",
        )

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = target,
            activeProfile = active,
        ) { screen ->
            assertNotNull(awaitText(screen.scene, profileName))
            assertNotNull(
                awaitText(screen.scene, unsupportedNotice),
                "A saved profile with a preserved unsupported section should show the shared notice",
            )
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
        }
    }

    @Test
    fun leavingAnUnchangedExistingProfileEditorDoesNotWriteTheLibrary() = runBlocking {
        val profileName = "Inactive profile selected for edit"
        val sourceUrl = "https://inactive.example.test/profile.conf"
        val target = normalizedProfile(
            id = 7,
            name = profileName,
            sourceUrl = sourceUrl,
            updateLocked = true,
            content = """[General]
loglevel = warning
""",
        )
        val active = normalizedProfile(
            id = 3,
            name = "Active profile remains selected",
            sourceUrl = "",
            updateLocked = false,
            content = """[General]
loglevel = debug
""",
        )
        val editLabel = getString(Res.string.configs_ui_edit)

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = target,
            activeProfile = active,
        ) { screen ->
            clickRowContentDescriptionAction(screen.scene, profileName, editLabel)
            assertEquals(profileName, awaitEditableText(screen.scene, profileName), "Edit should open the requested existing profile")
            assertEquals(sourceUrl, awaitEditableText(screen.scene, sourceUrl), "The editor should retain the selected profile's URL")

            clickTopBarBack(screen.scene)

            assertNotNull(awaitText(screen.scene, profileName), "Back should leave the unchanged editor and return to the profile list")
            assertEquals(screen.initialLibrary, screen.persistedLibrary())
            assertEquals(3, screen.persistedLibrary().selectedConfigId)
            assertEquals(0, screen.libraryTransformCount(), "Leaving an unchanged profile editor must not write to the library")
        }
    }

    @Test
    fun openingGroupsFromBasicsAndBackingOutReturnsToRouting() = runBlocking {
        val profileName = "Profile for routing navigation baseline"
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val resourcesTitle = getString(Res.string.configs_resources)
        val editLabel = getString(Res.string.configs_ui_edit)
        val target = normalizedProfile(
            id = 7,
            name = profileName,
            content = """[General]
loglevel = warning
""",
        )
        val active = normalizedProfile(
            id = 3,
            name = "Active profile for routing navigation baseline",
            content = """[General]
loglevel = debug
""",
        )

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = target,
            activeProfile = active,
        ) { screen ->
            clickRowContentDescriptionAction(screen.scene, profileName, editLabel)
            assertEquals(profileName, awaitEditableText(screen.scene, profileName))
            clickTextAction(screen.scene, routingTitle)
            assertNotNull(awaitText(screen.scene, routingTitle), "The Basics section should open Routing")
            clickTextAction(screen.scene, groupsTitle)
            assertNotNull(awaitText(screen.scene, groupsTitle), "Routing should open its Proxy groups section")

            clickTopBarBack(screen.scene)

            assertNotNull(awaitText(screen.scene, routingTitle), "Back from Proxy groups should return to Routing")
            assertNotNull(awaitText(screen.scene, resourcesTitle), "The returned Routing screen should show its Resources row")
            assertNotNull(awaitText(screen.scene, groupsTitle), "The returned Routing screen should show its Proxy groups row")
            assertEquals(null, awaitEditableText(screen.scene, profileName), "Back should not return to the Basics form")
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Navigating between unchanged editor sections must not write the profile library")
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(emptyList(), screen.applyRequests())
        }
    }

    @Test
    fun emptyLibraryShowsLegacyEmptyCardAndToolbarImportChoicesWithoutIo() = runBlocking {
        val emptyLibrary = DesktopConfigLibrary(
            selectedConfigId = null,
            configs = emptyList(),
            nextConfigId = 1,
        )
        val addConfigLabel = "Добавить конфиг"

        withDesktopScreen(
            proxyServers = emptyList(),
            initialLibraryOverride = emptyLibrary,
        ) { screen ->
            assertNotNull(awaitText(screen.scene, "Пока нет конфигов"), "An empty library should show the existing empty-state card")
            assertNotNull(awaitText(screen.scene, "Создайте профиль, откройте .conf или импортируйте ссылку."))
            assertNotNull(awaitText(screen.scene, "Новый конфиг"), "The empty-state card should retain its create action")

            clickContentDescriptionAction(screen.scene, addConfigLabel)

            assertNotNull(awaitText(screen.scene, "Новый конфиг"), "Toolbar Add should offer new-config creation")
            assertNotNull(awaitText(screen.scene, "Открыть .conf"), "Toolbar Add should offer a .conf file import")
            assertNotNull(awaitText(screen.scene, "Импортировать из буфера"), "Toolbar Add should offer clipboard import")
            assertNotNull(awaitText(screen.scene, "Импортировать по URL"), "Toolbar Add should offer URL import")
            assertEquals(emptyLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount(), "Checking empty-state choices must not persist a config")
            assertEquals(emptyList(), screen.applyRequests())
        }
    }

    @Test
    fun rendersCollectionAndGlobalActionsAtNarrowAndWideViewports() = runBlocking {
        val globalGroupsTitle = getString(Res.string.configs_global_proxy_groups)
        val sourcedGroupsTitle = getString(Res.string.configs_proxy_groups_title)
        val profilesTitle = getString(Res.string.configs_profiles)
        val groupEditorTitle = getString(Res.string.proxy_editor_strategy_group_title)
        val duplicateLabel = getString(Res.string.configs_duplicate)
        val editLabel = getString(Res.string.common_edit)
        val deleteLabel = getString(Res.string.common_delete)
        val cancelLabel = getString(Res.string.common_cancel)
        val saveLabel = getString(Res.string.common_save)
        val confirmationTitle = getString(Res.string.deletion_confirmation_delete_proxy_server)
        val discardTitle = getString(Res.string.proxy_editor_discard_changes_title)
        val profileName = "Responsive collection profile metadata"
        val sourceUrl = "https://responsive.example.test/profile.conf"
        val sourceGroupName = "Responsive profile source group"
        val target = normalizedProfile(
            id = 7,
            name = profileName,
            sourceUrl = sourceUrl,
            updateLocked = true,
            content = """[Rule]
DOMAIN-SUFFIX,example.com,PROXY
IP-CIDR,10.0.0.0/8,DIRECT
FINAL,DIRECT
malformed rule row

[Proxy Group]
$sourceGroupName = select, DIRECT
Responsive profile probe group = url-test, DIRECT, url=https://probe.example.test/ping
""",
        )
        val active = normalizedProfile(
            id = 3,
            name = "Responsive active collection profile",
            sourceUrl = "",
            updateLocked = false,
            content = """[General]
loglevel = debug
""",
        )
        val globalGroupName = "Responsive global collection group"
        val proxyServers = listOf(
            catalogServer(
                id = 40,
                sourceSubscriptionId = DefaultAutoBalancerGroupId,
                server = StrategyGroup(
                    remarks = globalGroupName,
                    probeUrl = "https://responsive.example.test/probe",
                ),
            ),
        )

        for ((width, height) in listOf(390 to 844, 1100 to 800)) {
            withDesktopScreen(
                proxyServers = proxyServers,
                targetProfile = target,
                activeProfile = active,
                viewportWidth = width,
                viewportHeight = height,
            ) { screen ->
                assertVisibleViewportText(screen.scene, globalGroupsTitle, width, height)
                assertVisibleViewportText(screen.scene, globalGroupName, width, height)
                assertVisibleViewportText(screen.scene, sourcedGroupsTitle, width, height)
                assertVisibleViewportText(screen.scene, sourceGroupName, width, height)
                saveViewportSnapshot(screen.scene, "collection-global-sourced", width, height)

                scrollViewportUntilAllVisible(
                    scene = screen.scene,
                    width = width,
                    height = height,
                    predicates = listOf<(SemanticsNode) -> Boolean>(
                        { it.hasText(profilesTitle) },
                        { it.hasText(profileName) },
                        { it.hasText(sourceUrl) },
                        { it.hasText("Правила: 3 · Группы: 2") },
                        { it.hasTextContaining("Обновление заблокировано") },
                        { it.hasTextContaining("Диагностика: 1") },
                        { it.hasContentDescription("Копировать содержимое") },
                        { it.hasContentDescription("Ещё") },
                    ),
                    predicateNames = listOf(
                        profilesTitle,
                        profileName,
                        sourceUrl,
                        "Правила: 3 · Группы: 2",
                        "Обновление заблокировано",
                        "Диагностика: 1",
                        "Копировать содержимое",
                        "Ещё",
                    ),
                )
                saveViewportSnapshot(screen.scene, "collection-profile-metadata-actions", width, height)

                scrollViewportUntilVisible(screen.scene, globalGroupName, width, height, deltaSign = -1f)
                clickTextAction(screen.scene, globalGroupName)
                assertVisibleViewportText(screen.scene, groupEditorTitle, width, height)
                assertVisibleViewportContentDescription(screen.scene, saveLabel, width, height)
                saveViewportSnapshot(screen.scene, "global-group-editor", width, height)

                clickTopBarBack(screen.scene)
                awaitRendererCatalogRow(
                    scene = screen.scene,
                    rowLabel = globalGroupName,
                    editorTitle = groupEditorTitle,
                    discardTitle = discardTitle,
                    width = width,
                    height = height,
                )
                clickRowOnLongClickAction(screen.scene, globalGroupName)
                assertVisibleViewportText(screen.scene, editLabel, width, height)
                assertVisibleViewportText(screen.scene, duplicateLabel, width, height)
                settleTransientLayer(screen.scene, "global-group context menu")
                saveViewportSnapshot(screen.scene, "global-group-context-menu", width, height)

                clickTextAction(screen.scene, deleteLabel)
                assertVisibleViewportText(screen.scene, confirmationTitle, width, height)
                settleTransientLayer(screen.scene, "global-group delete confirmation")
                saveViewportSnapshot(screen.scene, "global-group-delete-confirmation", width, height)
                clickDialogTextAction(screen.scene, confirmationTitle, cancelLabel)
                settleTransientLayer(screen.scene, "global-group delete confirmation dismissal")
                assertVisibleViewportText(screen.scene, globalGroupName, width, height)

                assertEquals(emptyList(), screen.applyRequests(), "Rendering and opening a confirmation must not apply a global result")
                assertEquals(screen.initialLibrary, screen.persistedLibrary())
                assertEquals(0, screen.libraryTransformCount())
                assertEquals(proxyServers, screen.proxyServers)
            }
        }
    }

    private fun withDesktopScreen(
        proxyServers: List<CatalogProxyServerRecord>,
        scriptedApplyOutcome: ProxyServerEditApplyOutcome? = null,
        targetProfile: ConfigProfile? = null,
        activeProfile: ConfigProfile? = null,
        initialLibraryOverride: DesktopConfigLibrary? = null,
        viewportWidth: Int = TestSceneWidth,
        viewportHeight: Int = TestSceneHeight,
        block: (DesktopScreenHarness) -> Unit,
    ) {
        withTemporaryRoot { root ->
            val target = targetProfile ?: normalizedProfile(
                id = 7,
                name = "Inactive target profile",
                content = """[General]
loglevel = warning

[Proxy Group]
$TargetProfileGroupName = select, DIRECT
""",
            )
            val active = activeProfile ?: normalizedProfile(
                id = 3,
                name = "Active profile",
                content = """[General]
loglevel = debug

[Proxy Group]
$ActiveProfileGroupName = select, DIRECT
""",
            )
            val initialLibrary = initialLibraryOverride ?: DesktopConfigLibrary(
                selectedConfigId = active.id,
                configs = listOf(target, active),
                nextConfigId = 29,
            )
            var latestLibrary by mutableStateOf(initialLibrary)
            var persistedLibrary = initialLibrary
            var libraryTransformCount = 0
            val applyRequests = mutableListOf<GlobalProxyGroupApplyRequest>()
            val transformLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary = { transform ->
                libraryTransformCount++
                transform(latestLibrary).also { updated ->
                    persistedLibrary = updated
                    latestLibrary = updated
                }
            }

            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val resourceServices = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { it.id == profileId }
                        ?.toTrafficConfigState(TestResourceUserAgent)
                },
                activeProxy = { null },
                prepareFeedback = {
                    DesktopResourceFileUpdateFeedback(
                        onProgress = { _, _, _ -> },
                        onComplete = {},
                        onFailure = {},
                        mapCancellationError = { it },
                    )
                },
                pickFile = { null },
            )
            val scene = ImageComposeScene(width = viewportWidth, height = viewportHeight)
            frameTimeNanos = 1_000_000_000L
            try {
                scene.setContent {
                    ProvideSkipiTheme(
                        colors = resolveBaseAppearanceColors(isDark = false),
                        fontFamily = null,
                        fontSizeMode = null,
                        fontWeightMode = null,
                        visualProfile = SkipiVisualProfile.Desktop,
                    ) {
                        DesktopConfigsScreen(
                            configLibrary = latestLibrary,
                            onTransformConfigLibrary = transformLibrary,
                            fetchUserAgent = TestResourceUserAgent,
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(confirmDeletion = true),
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in collection test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in collection test") },
                            onApplyGlobalProxyGroupResult = { result, afterSave ->
                                val outcome = scriptedApplyOutcome
                                    ?: error("Unexpected global proxy-group result apply in collection test")
                                applyRequests += result.toGlobalProxyGroupApplyRequest()
                                afterSave(outcome)
                            },
                            proxyServers = proxyServers,
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            resourceServices = resourceServices,
                            resourceFileStatusMessage = "",
                        )
                    }
                }
                render(scene)
                block(
                    DesktopScreenHarness(
                        scene = scene,
                        initialLibrary = initialLibrary,
                        persistedLibrary = { persistedLibrary },
                        libraryTransformCount = { libraryTransformCount },
                        applyRequests = { applyRequests.toList() },
                        proxyServers = proxyServers,
                    ),
                )
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    private data class DesktopScreenHarness(
        val scene: ImageComposeScene,
        val initialLibrary: DesktopConfigLibrary,
        val persistedLibrary: () -> DesktopConfigLibrary,
        val libraryTransformCount: () -> Int,
        val applyRequests: () -> List<GlobalProxyGroupApplyRequest>,
        val proxyServers: List<CatalogProxyServerRecord>,
    )

    private data class GlobalProxyGroupApplyRequest(
        val serverId: Int?,
        val server: StrategyGroup,
        val groupId: Int?,
        val returnGroupId: Int?,
        val deleted: Boolean,
    )

    private fun ProxyServerEditResult.toGlobalProxyGroupApplyRequest() = GlobalProxyGroupApplyRequest(
        serverId = serverId,
        server = (server as? StrategyGroup)?.let { group ->
            group.copy(proxyServerIds = group.proxyServerIds.toList())
        } ?: error("A global proxy-group result must contain a StrategyGroup"),
        groupId = groupId,
        returnGroupId = returnGroupId,
        deleted = deleted,
    )

    private fun globalStrategy(servers: List<CatalogProxyServerRecord>, id: Int): StrategyGroup =
        requireNotNull(servers.single { it.id == id }.server as? StrategyGroup)

    private fun normalizedProfile(id: Int, name: String, content: String): ConfigProfile {
        return normalizedProfile(
            id = id,
            name = name,
            content = content,
            sourceUrl = "https://example.test/$id.conf",
            updateLocked = true,
        )
    }

    private fun normalizedProfile(
        id: Int,
        name: String,
        content: String,
        sourceUrl: String,
        updateLocked: Boolean,
    ): ConfigProfile {
        val profile = ConfigProfile(
            id = id,
            name = name,
            content = content,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            lastUpdatedAtMillis = 456L,
        )
        return profile.copy(
            content = profile.toTrafficConfigState(TestResourceUserAgent)
                .withSkipiSettingsInRawConfig()
                .rawConfig,
        )
    }

    private fun catalogServer(
        id: Int,
        sourceSubscriptionId: Int?,
        server: features.proxy.server.model.ProxyServer<*>,
    ) = CatalogProxyServerRecord(
        id = id,
        sourceSubscriptionId = sourceSubscriptionId,
        server = server,
    )

    private fun clickTextAction(scene: ImageComposeScene, label: String) {
        val node = assertNotNull(awaitText(scene, label), "Expected rendered text '$label'")
        val target = assertNotNull(node.nearestClickableAncestor(), "Expected '$label' to have a clickable ancestor")
        val onClick = assertNotNull(
            target.config[SemanticsActions.OnClick].action,
            "Expected '$label' to expose its public OnClick action",
        )
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun clickContentDescriptionAction(scene: ImageComposeScene, label: String) {
        val node = assertNotNull(awaitContentDescription(scene, label), "Expected accessible control '$label'")
        clickNodeAction(scene, node.nearestClickableAncestor() ?: node, "The '$label' control")
    }

    private fun clickRowContentDescriptionAction(scene: ImageComposeScene, rowLabel: String, actionLabel: String) {
        val row = assertNotNull(
            rowCards(scene, rowLabel).firstOrNull { candidate ->
                candidate.descendants().any { it.hasContentDescription(actionLabel) }
            },
            "Expected clickable row '$rowLabel' with action '$actionLabel'",
        )
        val actionNode = assertNotNull(
            row.descendants().firstOrNull { it.hasContentDescription(actionLabel) },
            "Expected '$actionLabel' inside row '$rowLabel'",
        )
        val clickTarget = assertNotNull(
            actionNode.nearestClickableAncestor(),
            "Expected '$actionLabel' inside '$rowLabel' to have a clickable ancestor",
        )
        clickNodeAction(scene, clickTarget, "The '$actionLabel' action in '$rowLabel'")
    }

    private fun rowHasContentDescriptionAction(scene: ImageComposeScene, rowLabel: String, actionLabel: String): Boolean {
        return rowCards(scene, rowLabel).any { row ->
            row.descendants().any { candidate -> candidate.hasContentDescription(actionLabel) }
        }
    }

    private fun clickRowOnLongClickAction(scene: ImageComposeScene, rowLabel: String) {
        val row = assertNotNull(rowCard(scene, rowLabel), "Expected clickable row '$rowLabel'")
        val longClickTarget = assertNotNull(
            row.ancestorsIncludingSelf().firstOrNull { candidate -> candidate.config.contains(SemanticsActions.OnLongClick) },
            "Expected '$rowLabel' to expose its public OnLongClick action",
        )
        val onLongClick = assertNotNull(
            longClickTarget.config[SemanticsActions.OnLongClick].action,
            "Expected '$rowLabel' to expose its public OnLongClick action",
        )
        assertTrue(onLongClick(), "The '$rowLabel' OnLongClick action should succeed")
        render(scene)
    }

    private fun clickDialogTextAction(scene: ImageComposeScene, dialogTitle: String, label: String) {
        val root = assertNotNull(
            currentRoots(scene).firstOrNull { candidate ->
                candidate.descendants().any { node -> node.hasText(dialogTitle) } &&
                    candidate.descendants().any { node -> node.hasText(label) && node.nearestClickableAncestor() != null }
            },
            "Expected '$label' inside dialog '$dialogTitle'",
        )
        val node = assertNotNull(
            root.descendants().firstOrNull { candidate -> candidate.hasText(label) && candidate.nearestClickableAncestor() != null },
            "Expected clickable dialog action '$label' inside '$dialogTitle'",
        )
        val target = assertNotNull(node.nearestClickableAncestor())
        clickNodeAction(scene, target, "The '$label' dialog action")
    }

    private fun clickNodeAction(scene: ImageComposeScene, node: SemanticsNode, description: String) {
        val onClick = assertNotNull(
            node.config[SemanticsActions.OnClick].action,
            "$description should expose its public OnClick action",
        )
        assertTrue(onClick(), "$description should succeed")
        render(scene)
    }

    private fun setFirstEditableText(scene: ImageComposeScene, value: String) {
        val field = assertNotNull(
            awaitNode(scene) { candidate ->
                candidate.config.contains(SemanticsActions.SetText) && candidate.config.contains(SemanticsProperties.EditableText)
            },
            "Expected a visible field to expose SetText semantics",
        )
        val setTextAction = assertNotNull(field.config[SemanticsActions.SetText].action)
        assertTrue(setTextAction(AnnotatedString(value)), "The visible field should accept test input")
        render(scene)
    }

    private fun awaitEditableText(scene: ImageComposeScene, expectedValue: String): String? {
        val field = awaitNode(scene) { candidate ->
            candidate.config.contains(SemanticsProperties.EditableText) &&
                candidate.config[SemanticsProperties.EditableText].text == expectedValue
        }
        return field?.config?.get(SemanticsProperties.EditableText)?.text
    }

    private fun clickTopBarBack(scene: ImageComposeScene) {
        val back = assertNotNull(awaitNode(scene) { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) &&
                center.x in 0f..72f && center.y in 0f..80f
        }, "Expected a visible top-left Back control")
        val onClick = assertNotNull(back.config[SemanticsActions.OnClick].action)
        assertTrue(onClick(), "The Back action should succeed")
        render(scene)
    }

    private fun awaitText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene) { candidate -> candidate.hasText(label) }

    private fun awaitTextContaining(scene: ImageComposeScene, fragment: String): SemanticsNode? =
        awaitNode(scene) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.contains(fragment, ignoreCase = true) }
        }

    private fun awaitContentDescription(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene) { candidate -> candidate.hasContentDescription(label) }

    private fun rowCard(scene: ImageComposeScene, label: String): SemanticsNode? {
        return rowCards(scene, label).firstOrNull()
    }

    private fun rowCards(scene: ImageComposeScene, label: String): List<SemanticsNode> {
        val candidates = currentNodes(scene).filter { it.hasText(label) }
            .flatMap { textNode ->
                textNode.ancestorsIncludingSelf().filter { candidate ->
                    candidate.config.contains(SemanticsActions.OnClick) &&
                        candidate.descendants().any { it.hasText(label) }
                }
            }
            .toList()
        return candidates.distinctBy(SemanticsNode::id)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun currentRoots(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence().map { owner -> owner.unmergedRootSemanticsNode }

    private fun hasVisibleText(scene: ImageComposeScene, label: String): Boolean =
        currentNodes(scene).any { candidate -> candidate.hasText(label) }

    private fun assertVisibleViewportText(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        assertNotNull(
            awaitViewportNode(scene, width, height) { candidate -> candidate.hasText(label) },
            "Expected rendered text '$label' inside the ${width}x$height viewport",
        )
    }

    private fun assertVisibleViewportContentDescription(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ) {
        assertNotNull(
            awaitViewportNode(scene, width, height) { candidate -> candidate.hasContentDescription(label) },
            "Expected accessible control '$label' inside the ${width}x$height viewport",
        )
    }

    private fun scrollViewportUntilVisible(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
        deltaSign: Float = 1f,
    ) {
        scrollViewportUntilAllVisible(
            scene = scene,
            width = width,
            height = height,
            deltaSign = deltaSign,
            predicates = listOf<(SemanticsNode) -> Boolean>({ candidate -> candidate.hasText(label) }),
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun scrollViewportUntilAllVisible(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
        predicates: List<(SemanticsNode) -> Boolean>,
        predicateNames: List<String> = emptyList(),
        deltaSign: Float = 1f,
    ) {
        val deadline = System.nanoTime() + 5_000_000_000L
        var scrollSteps = 0
        do {
            render(scene)
            val nodes = allSceneNodes(scene).toList()
            val visibleNodes = nodes.filter { candidate -> candidate.centerIsOnScreen(width, height) }
            if (predicates.all { predicate -> visibleNodes.any(predicate) }) return

            if (scrollSteps >= 14) break
            val scrollNode = nodes.firstOrNull { candidate ->
                candidate.config.contains(SemanticsActions.ScrollBy) &&
                    candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            }
            val scrollAction = scrollNode?.config?.get(SemanticsActions.ScrollBy)?.action ?: break
            scrollAction(0f, deltaSign * height * 0.35f)
            scrollSteps++
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)

        val missingNames = predicates.indices
            .filter { index -> visibleNodesForPredicates(scene, width, height).none(predicates[index]) }
            .map { index -> predicateNames.getOrNull(index) ?: "predicate[$index]" }
        val relevantNodeBounds = allSceneNodes(scene).mapNotNull { node ->
            val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
            val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            val semanticsValues = text + descriptions
            val matchedValues = semanticsValues.filter { value ->
                predicateNames.any { expected -> value.contains(expected, ignoreCase = true) }
            }
            matchedValues.takeIf { it.isNotEmpty() }?.let { values ->
                "${values.joinToString(" | ")} @ ${node.boundsInRoot}"
            }
        }.take(50).toList()
        val snapshotName = "renderer-scroll-failure"
        val snapshotFile = File(
            System.getProperty("user.dir"),
            "build/reports/desktop-traffic-config-collection-responsive/$snapshotName-${width}x$height.png",
        )
        val snapshotFailure = runCatching {
            saveViewportSnapshot(scene, snapshotName, width, height)
        }.exceptionOrNull()?.message
        val snapshotStatus = if (snapshotFailure == null) {
            snapshotFile.absolutePath
        } else {
            "capture failed: $snapshotFailure"
        }
        error(
            "Could not place requested collection content inside the ${width}x$height viewport after $scrollSteps scroll steps; " +
                "missing visible predicates=$missingNames; tracked node bounds=$relevantNodeBounds; diagnostic PNG=$snapshotStatus",
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun visibleNodesForPredicates(scene: ImageComposeScene, width: Int, height: Int): List<SemanticsNode> =
        allSceneNodes(scene).filter { node -> node.centerIsOnScreen(width, height) }.toList()

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitViewportNode(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + 3_000_000_000L
        do {
            render(scene)
            allSceneNodes(scene)
                .firstOrNull { candidate -> candidate.centerIsOnScreen(width, height) && predicate(candidate) }
                ?.let { return it }
            Thread.sleep(8L)
        } while (System.nanoTime() < deadline)
        return null
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitRendererCatalogRow(
        scene: ImageComposeScene,
        rowLabel: String,
        editorTitle: String,
        discardTitle: String,
        width: Int,
        height: Int,
    ) {
        val row = awaitViewportNode(scene, width, height) { candidate ->
            candidate.hasText(rowLabel) &&
                candidate.ancestorsIncludingSelf().any { ancestor -> ancestor.config.contains(SemanticsActions.OnClick) } &&
                candidate.ancestorsIncludingSelf().any { ancestor -> ancestor.config.contains(SemanticsActions.OnLongClick) }
        }
        if (row != null) return

        val visibleNodes = allSceneNodes(scene)
            .filter { candidate -> candidate.centerIsOnScreen(width, height) }
            .toList()
        val visibleLabels = visibleNodes.flatMap { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
                candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        }.distinct()
        val stateDescription = when {
            discardTitle in visibleLabels -> "the editor discard confirmation is still open"
            editorTitle in visibleLabels -> "the group editor is still open"
            else -> "the catalog row and editor/dialog titles are not visible"
        }
        val snapshotName = "renderer-back-return-failure"
        val snapshotFile = File(
            System.getProperty("user.dir"),
            "build/reports/desktop-traffic-config-collection-responsive/$snapshotName-${width}x$height.png",
        )
        val snapshotFailure = runCatching {
            saveViewportSnapshot(scene, snapshotName, width, height)
        }.exceptionOrNull()?.message
        val snapshotStatus = if (snapshotFailure == null) {
            snapshotFile.absolutePath
        } else {
            "capture failed: $snapshotFailure"
        }
        error(
            "Back did not return to a clickable '$rowLabel' catalog row at ${width}x$height; $stateDescription; " +
                "visible public semantics=$visibleLabels; diagnostic PNG=$snapshotStatus",
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun allSceneNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        currentRoots(scene).flatMap { root -> root.descendants() }

    private fun SemanticsNode.centerIsOnScreen(width: Int, height: Int): Boolean {
        val center = boundsInRoot.center
        return boundsInRoot.width > 0f && boundsInRoot.height > 0f &&
            center.x in 0f..width.toFloat() && center.y in 0f..height.toFloat()
    }

    private fun saveViewportSnapshot(scene: ImageComposeScene, name: String, width: Int, height: Int) {
        val image = scene.render(advancingSystemNanoTime())
        try {
            assertEquals(width, image.width, "$name image width")
            assertEquals(height, image.height, "$name image height")
            assertTrue(!image.isEmpty, "$name at ${width}x$height should render visible content")

            val pixels = requireNotNull(image.peekPixels())
            try {
                val sampledColors = buildSet {
                    for (y in 0 until height step 24) {
                        for (x in 0 until width step 24) add(pixels.getColor(x, y))
                    }
                }
                assertTrue(sampledColors.size >= 3, "$name should render cards and controls")
            } finally {
                pixels.close()
            }

            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                val reportDirectory = File(
                    System.getProperty("user.dir"),
                    "build/reports/desktop-traffic-config-collection-responsive",
                )
                assertTrue(reportDirectory.mkdirs() || reportDirectory.isDirectory)
                val file = File(reportDirectory, "$name-${width}x$height.png")
                file.writeBytes(encoded.bytes)
                assertTrue(file.length() > 1_024L, "$name PNG should contain the rendered screen")
            } finally {
                encoded.close()
            }
        } finally {
            image.close()
        }
    }

    private fun settleTransientLayer(scene: ImageComposeScene, layerDescription: String) {
        // Semantics appear as soon as visibility state changes; advance the real Compose frame clock
        // before capturing and require the rendered image itself to stop changing.
        val minimumFrameCount = 30
        val requiredStableFrames = 3
        val maximumFrameCount = 90
        var firstFramePixels: IntArray? = null
        var secondFramePixels: IntArray? = null
        var previousPixels: IntArray? = null
        var stableFrames = 0

        repeat(maximumFrameCount) { frameIndex ->
            val image = scene.render(advancingSystemNanoTime())
            try {
                val pixelMap = requireNotNull(image.peekPixels())
                try {
                    val pixelCount = image.width * image.height
                    if (firstFramePixels == null) {
                        firstFramePixels = IntArray(pixelCount)
                        secondFramePixels = IntArray(pixelCount)
                    }
                    val first = requireNotNull(firstFramePixels)
                    val second = requireNotNull(secondFramePixels)
                    val currentPixels = if (previousPixels === first) second else first
                    var pixelIndex = 0
                    for (y in 0 until image.height) {
                        for (x in 0 until image.width) {
                            currentPixels[pixelIndex++] = pixelMap.getColor(x, y)
                        }
                    }

                    val previous = previousPixels
                    if (previous != null && currentPixels.contentEquals(previous)) {
                        stableFrames++
                    } else {
                        stableFrames = 0
                    }
                    previousPixels = currentPixels
                } finally {
                    pixelMap.close()
                }
            } finally {
                image.close()
            }

            if (frameIndex + 1 >= minimumFrameCount && stableFrames >= requiredStableFrames) return
        }

        error(
            "The $layerDescription did not reach a stable rendered frame after " +
                "$maximumFrameCount advanced Compose frames",
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitNode(
        scene: ImageComposeScene,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + 3_000_000_000L
        var previousSample: SemanticsNodeSample? = null
        var stableFrames = 0
        do {
            render(scene)
            val candidate = currentNodes(scene).firstOrNull(predicate)
            val sample = candidate?.let { node -> SemanticsNodeSample(node.id, node.boundsInRoot) }
            if (sample != null && sample == previousSample) {
                stableFrames++
                if (stableFrames >= 1) return candidate
            } else {
                stableFrames = 0
            }
            previousSample = sample
            Thread.sleep(1L)
        } while (System.nanoTime() < deadline)
        return null
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun currentNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
            .filter { candidate -> candidate.hasVisibleBounds() }

    private data class SemanticsNodeSample(
        val id: Int,
        val bounds: androidx.compose.ui.geometry.Rect,
    )

    private fun SemanticsNode.hasVisibleBounds(): Boolean {
        val bounds = boundsInRoot
        return bounds.width > 0f && bounds.height > 0f &&
            bounds.left >= 0f && bounds.top >= 0f &&
            bounds.right <= TestSceneWidth.toFloat() && bounds.bottom <= TestSceneHeight.toFloat()
    }

    private fun SemanticsNode.hasText(expected: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { text -> text.text.equals(expected, ignoreCase = true) }

    private fun SemanticsNode.hasTextContaining(fragment: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { text -> text.text.contains(fragment, ignoreCase = true) }

    private fun SemanticsNode.hasContentDescription(expected: String): Boolean =
        config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .any { description -> description.equals(expected, ignoreCase = true) }

    private fun SemanticsNode.nearestClickableAncestor(): SemanticsNode? =
        ancestorsIncludingSelf().firstOrNull { candidate -> candidate.config.contains(SemanticsActions.OnClick) }

    private fun SemanticsNode.ancestorsIncludingSelf(): Sequence<SemanticsNode> = sequence {
        var current: SemanticsNode? = this@ancestorsIncludingSelf
        var remaining = 8
        while (current != null && remaining > 0) {
            yield(current)
            current = current.parent
            remaining--
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { child -> yieldAll(child.descendants()) }
    }

    private fun render(scene: ImageComposeScene) {
        scene.render(advancingSystemNanoTime()).close()
    }

    private fun advancingSystemNanoTime(): Long {
        frameTimeNanos += 16_666_667L
        return frameTimeNanos
    }

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("desktop-config-collection-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<java.nio.file.Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }
}
