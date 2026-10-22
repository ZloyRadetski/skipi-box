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
import app.skipi.ui.resources.common_refresh
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
import app.skipi.ui.resources.configs_raw_edit
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.configs_update_anyway
import app.skipi.ui.resources.configs_update_locked_warning_title
import app.skipi.ui.resources.deletion_confirmation_delete_proxy_server
import app.skipi.ui.resources.proxy_editor_discard
import app.skipi.ui.resources.proxy_editor_discard_changes_title
import app.skipi.ui.resources.proxy_editor_strategy_group_title
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.skipiSectionValues
import features.config.withSkipiSettingsInRawConfig
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import engine.network.NetworkDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private data class RawEditorLibraryFixture(
        val target: ConfigProfile,
        val sibling: ConfigProfile,
        val library: DesktopConfigLibrary,
    )

    private fun rawEditorLibraryFixture(): RawEditorLibraryFixture {
        val target = normalizedProfile(
            id = 7,
            name = "Raw editor target",
            sourceUrl = "https://old.example/profile.conf",
            updateLocked = false,
            content = """[General]
loglevel = warning

[Proxy]
server = old.example:443
""",
        )
        val sibling = normalizedProfile(
            id = 3,
            name = "Selected sibling remains unchanged",
            sourceUrl = "https://sibling.example/profile.conf",
            updateLocked = false,
            content = """[General]
loglevel = error

[Proxy]
server = sibling.example:443
""",
        )
        return RawEditorLibraryFixture(
            target = target,
            sibling = sibling,
            library = DesktopConfigLibrary(
                selectedConfigId = sibling.id,
                configs = listOf(target, sibling),
                nextConfigId = 29,
            ),
        )
    }

    @Test
    fun rawEditMenuBackSavesValidAndroidDocumentAndPreservesLibraryEnvelope() = runBlocking {
        val fixture = rawEditorLibraryFixture()
        val rawEditLabel = getString(Res.string.configs_raw_edit)
        // Kept byte-for-byte aligned with TrafficConfigEditorAndroidOracleTest's
        // Android raw-save fixture (including its General, Proxy, SKIPI, Vendor data).
        val rawDraft = """# edited in Android raw editor
[General]
loglevel = debug

[Proxy]
server = raw.example:443

[SKIPI]
profile-name = Raw editor name
profile-update-url = https://raw.example/profile.conf
profile-update-locked = true
profile-auto-update = false
profile-update-interval = 48
resource-auto-update = false
resource-update-interval = 96

[Vendor]
vendor-option = keep-on-raw-save"""

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = fixture.target,
            activeProfile = fixture.sibling,
            initialLibraryOverride = fixture.library,
        ) { screen ->
            // Scope More to the actual profile row so a duplicate profile/group label
            // cannot route the test through the sourced-group row.
            clickRowContentDescriptionAction(screen.scene, fixture.target.name, "Ещё")
            clickTextAction(screen.scene, rawEditLabel)
            assertNotNull(awaitText(screen.scene, "Редактор конфига"))
            setEditableTextByCurrentValue(screen.scene, fixture.target.content, rawDraft)
            clickTopBarBack(screen.scene)

            assertNotNull(awaitText(screen.scene, "Raw editor name"), "Back should return to the collection after a valid raw save")
            assertEquals(1, screen.libraryTransformCount())
            val committed = screen.persistedLibrary()
            val saved = committed.configs.single { it.id == fixture.target.id }
            assertEquals(listOf(7, 3), committed.configs.map(ConfigProfile::id))
            assertEquals(3, committed.selectedConfigId)
            assertEquals(29, committed.nextConfigId)
            assertEquals(fixture.sibling, committed.configs.single { it.id == fixture.sibling.id })

            assertEquals(7, saved.id)
            assertEquals("Raw editor name", saved.name)
            assertEquals("https://raw.example/profile.conf", saved.sourceUrl)
            assertTrue(saved.updateLocked)
            assertEquals(456L, saved.lastUpdatedAtMillis)
            val skipi = saved.content.skipiSectionValues()
            assertEquals("Raw editor name", skipi["profile-name"]?.single())
            assertEquals("https://raw.example/profile.conf", skipi["profile-update-url"]?.single())
            assertEquals("true", skipi["profile-update-locked"]?.single())
            assertEquals("false", skipi["profile-auto-update"]?.single())
            assertEquals("48", skipi["profile-update-interval"]?.single())
            assertEquals("false", skipi["resource-auto-update"]?.single())
            assertEquals("96", skipi["resource-update-interval"]?.single())
            assertEquals("debug", saved.content.analyzeShadowrocketConfig().general["loglevel"])
            assertTrue(saved.content.startsWith("# edited in Android raw editor\n"))
            assertTrue(saved.content.contains("[Proxy]\nserver = raw.example:443"))
            assertTrue(saved.content.contains("[Vendor]\nvendor-option = keep-on-raw-save"))
            assertTrue(saved.content.endsWith("\n"))
            assertFalse(saved.content.endsWith("\n\n"))
        }
    }

    @Test
    fun rawEditMenuExplicitDesktopSaveKeepsPortableMetadataAndLibraryEnvelope() = runBlocking {
        val fixture = rawEditorLibraryFixture()
        val rawEditLabel = getString(Res.string.configs_raw_edit)
        val editedDocument = fixture.target.content.replace("loglevel = warning", "loglevel = debug")

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = fixture.target,
            activeProfile = fixture.sibling,
            initialLibraryOverride = fixture.library,
        ) { screen ->
            clickRowContentDescriptionAction(screen.scene, fixture.target.name, "Ещё")
            clickTextAction(screen.scene, rawEditLabel)
            assertNotNull(awaitText(screen.scene, "Редактор конфига"))

            setEditableTextByCurrentValue(screen.scene, fixture.target.name, "Saved from raw form")
            setEditableTextByCurrentValue(
                screen.scene,
                fixture.target.sourceUrl,
                "https://saved.example/profile.conf",
            )
            clickTextAction(screen.scene, "Блокировать обновление")
            setEditableTextByCurrentValue(screen.scene, fixture.target.content, editedDocument)
            clickTextAction(screen.scene, "Сохранить")

            assertNotNull(awaitText(screen.scene, "Saved from raw form"), "Save should return to the profile collection")
            assertEquals(1, screen.libraryTransformCount())
            val committed = screen.persistedLibrary()
            val saved = committed.configs.single { it.id == fixture.target.id }
            assertEquals(listOf(7, 3), committed.configs.map(ConfigProfile::id))
            assertEquals(3, committed.selectedConfigId)
            assertEquals(29, committed.nextConfigId)
            assertEquals(fixture.sibling, committed.configs.single { it.id == fixture.sibling.id })

            assertEquals(7, saved.id)
            assertEquals("Saved from raw form", saved.name)
            assertEquals("https://saved.example/profile.conf", saved.sourceUrl)
            assertTrue(saved.updateLocked)
            assertEquals(456L, saved.lastUpdatedAtMillis)
            val skipi = saved.content.skipiSectionValues()
            assertEquals("Saved from raw form", skipi["profile-name"]?.single())
            assertEquals("https://saved.example/profile.conf", skipi["profile-update-url"]?.single())
            assertEquals("true", skipi["profile-update-locked"]?.single())
            assertEquals("debug", saved.content.analyzeShadowrocketConfig().general["loglevel"])
            assertTrue(saved.content.endsWith("\n"))
            assertFalse(saved.content.endsWith("\n\n"))
        }
    }

    @Test
    fun rawEditMenuBackRejectsInvalidDocumentAndLeavesLibraryUnchanged() = runBlocking {
        val fixture = rawEditorLibraryFixture()
        val rawEditLabel = getString(Res.string.configs_raw_edit)
        val invalidDocument = """[Rule]
FINAL,PROXY
FINAL,DIRECT"""

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = fixture.target,
            activeProfile = fixture.sibling,
            initialLibraryOverride = fixture.library,
        ) { screen ->
            clickRowContentDescriptionAction(screen.scene, fixture.target.name, "Ещё")
            clickTextAction(screen.scene, rawEditLabel)
            assertNotNull(awaitText(screen.scene, "Редактор конфига"))
            setEditableTextByCurrentValue(screen.scene, fixture.target.content, invalidDocument)
            assertNotNull(awaitText(screen.scene, "В конфиге есть ошибки"))
            clickTopBarBack(screen.scene)

            assertNotNull(awaitText(screen.scene, fixture.target.name), "Rejected raw content should still leave the editor")
            assertEquals(0, screen.libraryTransformCount(), "An Error diagnostic must reject the document before persistence")
            assertEquals(fixture.library, screen.persistedLibrary())
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

    @Test
    fun manualProfileRefreshUsesCapturedSnapshotAndAppliesResponseToLatestLibraryRow() = runBlocking {
        val originalUrl = "  https://profiles.example.test/manual.conf  "
        val trimmedUrl = originalUrl.trim()
        val originalProfile = normalizedProfile(
            id = 7,
            name = "Captured profile name",
            sourceUrl = originalUrl,
            updateLocked = false,
            content = """[General]
loglevel = warning

[SKIPI]
profile-auto-update = false
profile-update-interval = 18
resource-auto-update = true
resource-update-interval = 33
resource-user-agent = Captured profile agent/1.0
""",
        )
        val enteredFetch = CompletableDeferred<ManualProfileFetchRequest>()
        val returnDocument = CompletableDeferred<String>()
        val fetchedDocument = """[General]
loglevel = debug

[SKIPI]
profile-update-url = https://provider.example.test/response-url.conf
profile-update-locked = true
profile-auto-update = true
profile-update-interval = 6
resource-auto-update = false
resource-update-interval = 12
resource-user-agent = Provider response agent/1.0
"""
        val active = normalizedProfile(
            id = 3,
            name = "Other active profile",
            sourceUrl = "",
            updateLocked = false,
            content = "[General]\nloglevel = info\n",
        )
        val startMillis = System.currentTimeMillis()

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = originalProfile,
            activeProfile = active,
            fetchProfileDocument = { profile, url ->
                enteredFetch.complete(ManualProfileFetchRequest(profile, url))
                withTimeout(5_000L) { returnDocument.await() }
            },
        ) { screen ->
            clickRowContentDescriptionAction(screen.scene, originalProfile.name, "Ещё")
            clickTextAction(screen.scene, getString(Res.string.common_refresh))

            val request = awaitDeferredWithRenderTicks(screen.scene, enteredFetch, "manual profile fetch")
            assertEquals(originalProfile.id, request.profile.id)
            assertEquals(originalProfile.name, request.profile.name)
            assertEquals(originalUrl, request.profile.sourceUrl, "The callback receives the captured stored snapshot")
            assertEquals("Captured profile agent/1.0", request.profile.resourceSettings.userAgent)
            assertEquals(trimmedUrl, request.url, "Only the fetch URL is trimmed")

            // Simulate a same-ID edit landing while HTTP is in flight. The response omits
            // profile-name so the shared manual policy must retain this latest row's name.
            val latestSourceUrl = "https://local.example.test/edited-during-fetch.conf"
            val latestTarget = normalizedProfile(
                id = originalProfile.id,
                name = "Latest local profile name",
                sourceUrl = latestSourceUrl,
                updateLocked = false,
                content = """[General]
loglevel = error

[SKIPI]
profile-auto-update = false
profile-update-interval = 21
resource-auto-update = true
resource-update-interval = 44
resource-user-agent = Latest local agent/1.0
""",
            )
            screen.replaceLatestLibrary(
                screen.initialLibrary.copy(
                    configs = screen.initialLibrary.configs.map { current ->
                        if (current.id == latestTarget.id) latestTarget else current
                    },
                ),
            )

            returnDocument.complete(fetchedDocument)
            val committed = awaitDeferredWithRenderTicks(
                screen.scene,
                screen.nextLibraryTransform,
                "manual refresh persistence",
            )
            val saved = committed.configs.single { it.id == originalProfile.id }
            val savedTyped = saved.toTrafficConfigState("Skipi/test/Desktop")

            assertEquals("Latest local profile name", saved.name)
            assertEquals(trimmedUrl, saved.sourceUrl, "The response URL and concurrent URL edit do not replace the captured URL")
            assertTrue(saved.updateLocked, "Unlocked manual refresh accepts the lock value from the response")
            assertTrue(saved.lastUpdatedAtMillis in startMillis..System.currentTimeMillis())
            assertTrue(saved.content.contains("loglevel = debug"))
            assertTrue(savedTyped.autoUpdate)
            assertEquals("6", savedTyped.updateInterval)
            assertFalse(savedTyped.resourceSettings.autoUpdate)
            assertEquals("12", savedTyped.resourceSettings.updateInterval)
            assertEquals("Provider response agent/1.0", savedTyped.resourceSettings.userAgent)
            assertTrue(saved.content.contains("profile-name = Latest local profile name"))
            assertTrue(saved.content.contains("profile-update-url = $trimmedUrl"))
            assertTrue(saved.content.contains("resource-user-agent = Provider response agent/1.0"))
            assertEquals(active.id, committed.selectedConfigId)
            assertEquals(screen.initialLibrary.nextConfigId, committed.nextConfigId)
            assertEquals(active, committed.configs.single { it.id == active.id })
            assertEquals(1, screen.libraryTransformCount(), "One accepted response should persist one latest-library transform")
        }
    }

    @Test
    fun manualLockedProfileRefreshWaitsForConfirmationAndExplicitlyUnlocksTheSavedProfile() = runBlocking {
        val originalUrl = "  https://profiles.example.test/locked.conf  "
        val trimmedUrl = originalUrl.trim()
        val lockedProfile = normalizedProfile(
            id = 7,
            name = "Locked captured profile",
            sourceUrl = originalUrl,
            updateLocked = true,
            content = """[General]
loglevel = warning

[SKIPI]
resource-user-agent = Locked profile agent/1.0
""",
        )
        val enteredFetch = CompletableDeferred<ManualProfileFetchRequest>()
        val response = """[General]
loglevel = debug

[SKIPI]
profile-name = Provider locked response
profile-update-locked = true
profile-auto-update = true
profile-update-interval = 9
resource-auto-update = false
resource-update-interval = 15
resource-user-agent = Provider locked agent/1.0
"""
        val warningTitle = getString(Res.string.configs_update_locked_warning_title)
        val updateAnyway = getString(Res.string.configs_update_anyway)

        withDesktopScreen(
            proxyServers = emptyList(),
            targetProfile = lockedProfile,
            fetchProfileDocument = { profile, url ->
                enteredFetch.complete(ManualProfileFetchRequest(profile, url))
                response
            },
        ) { screen ->
            clickRowContentDescriptionAction(screen.scene, lockedProfile.name, "Ещё")
            clickTextAction(screen.scene, getString(Res.string.common_refresh))
            assertNotNull(awaitText(screen.scene, warningTitle))
            assertFalse(enteredFetch.isCompleted, "A locked profile must not fetch before explicit confirmation")
            assertEquals(0, screen.libraryTransformCount(), "Showing the lock warning must not persist")

            clickTextAction(screen.scene, updateAnyway)
            val request = awaitDeferredWithRenderTicks(screen.scene, enteredFetch, "confirmed locked-profile fetch")
            assertEquals(lockedProfile.id, request.profile.id)
            assertTrue(request.profile.updateLocked)
            assertEquals("Locked profile agent/1.0", request.profile.resourceSettings.userAgent)
            assertEquals(trimmedUrl, request.url)

            val committed = awaitDeferredWithRenderTicks(
                screen.scene,
                screen.nextLibraryTransform,
                "confirmed manual refresh persistence",
            )
            val saved = committed.configs.single { it.id == lockedProfile.id }
            assertEquals("Provider locked response", saved.name)
            assertEquals(trimmedUrl, saved.sourceUrl)
            assertFalse(saved.updateLocked, "Explicit confirmation overrides a response that keeps the lock enabled")
            assertTrue(saved.content.contains("profile-update-locked = false"))
            assertEquals(1, screen.libraryTransformCount())
        }
    }

    @Test
    fun manualRefreshReconcilesLatestRepositoryAfterConcurrentEditAndActiveProfileSwitch() = runBlocking {
        val originalUrl = "https://profiles.example.test/reconcile.conf"
        val targetProfile = normalizedProfile(
            id = 7,
            name = "Captured target profile",
            sourceUrl = originalUrl,
            updateLocked = false,
            content = profileGroupDocument("Stable", listOf("Node A", "Node B")),
        )
        val siblingProfile = normalizedProfile(
            id = 8,
            name = "Sibling active profile",
            sourceUrl = "manual://sibling",
            updateLocked = false,
            content = profileGroupDocument("Sibling", listOf("Node C")),
        )
        val initialLibrary = DesktopConfigLibrary(
            selectedConfigId = targetProfile.id,
            configs = listOf(targetProfile, siblingProfile),
            nextConfigId = 80,
        )
        val initialServers = DesktopServerLibrary(
            selectedServerId = 99,
            servers = listOf(
                storedProfileServer(10, VLESS(remarks = "Node A", id = "a", server = "a.example", port = "443")),
                storedProfileServer(11, VLESS(remarks = "Node B", id = "b", server = "b.example", port = "443")),
                storedProfileServer(12, VLESS(remarks = "Node C", id = "c", server = "c.example", port = "443")),
                storedProfileServer(
                    30,
                    StrategyGroup(
                        remarks = "Stable",
                        proxyServerIds = listOf(10, 11),
                        selectedMemberId = 11,
                        sourceTrafficConfigId = targetProfile.id,
                        sourcePolicyGroupName = "Stable",
                    ),
                    subscriptionId = DefaultAutoBalancerGroupId,
                ),
                storedProfileServer(
                    25,
                    StrategyGroup(
                        remarks = "Sibling",
                        proxyServerIds = listOf(12),
                        selectedMemberId = 12,
                        sourceTrafficConfigId = siblingProfile.id,
                        sourcePolicyGroupName = "Sibling",
                    ),
                    subscriptionId = DefaultAutoBalancerGroupId,
                ),
                DesktopStoredProxyServer(id = 99, serverJson = """{"futureProxy":{"keep":true}}"""),
            ),
            nextServerId = 100,
        )
        val enteredFetch = CompletableDeferred<ManualProfileFetchRequest>()
        val returnDocument = CompletableDeferred<String>()
        val transformEvents = Channel<DesktopConfigLibrary>(Channel.UNLIMITED)
        val reconciliationEvents = Channel<DesktopConfigLibrary>(Channel.UNLIMITED)
        lateinit var configRepository: DesktopTrafficConfigRepository
        lateinit var readConfigs: () -> DesktopConfigLibrary
        lateinit var readServers: () -> DesktopServerLibrary
        lateinit var serverPath: java.nio.file.Path
        val response = """[General]
loglevel = info

[Proxy Group]
Stable = select, Node B, Node C, skipi-display=always

[Rule]
FINAL,DIRECT
"""

        try {
            withDesktopScreen(
                proxyServers = emptyList(),
                targetProfile = targetProfile,
                activeProfile = siblingProfile,
                initialLibraryOverride = initialLibrary,
                fetchProfileDocument = { profile, url ->
                    enteredFetch.complete(ManualProfileFetchRequest(profile, url))
                    withTimeout(5_000L) { returnDocument.await() }
                },
                libraryTransformHooksFactory = { root, initial, hostScope ->
                    val configPath = File(root, "configs.json").toPath()
                    serverPath = File(root, "servers.json").toPath()
                    DesktopConfigLibraries.save(configPath, initial).getOrThrow()
                    DesktopServerLibraries.save(serverPath, initialServers).getOrThrow()
                    readConfigs = { DesktopConfigLibraries.load(configPath).getOrThrow() }
                    readServers = { DesktopServerLibraries.load(serverPath).getOrThrow() }
                    configRepository = DesktopTrafficConfigRepository(
                        initialLibrary = readConfigs(),
                        readLibrary = readConfigs,
                        saveLibrary = { updated -> DesktopConfigLibraries.save(configPath, updated) },
                        publishLibrary = {},
                    )
                    val serverRepository = DesktopProxyServerRepository(
                        initialLibrary = readServers(),
                        readLibrary = readServers,
                        saveLibrary = { updated -> DesktopServerLibraries.save(serverPath, updated) },
                        publishLibrary = {},
                    )
                    val reconciler = DesktopTrafficConfigProxyGroupReconciler(
                        serverRepository = serverRepository,
                        readConfigLibrary = readConfigs,
                        stripLeadingCountryFlag = { it },
                    )
                    DesktopScreenLibraryTransformHooks(
                        transformLatestWithPrevious = configRepository::transformLatestWithPrevious,
                        afterTransform = { result ->
                            transformEvents.trySend(result.updated).getOrThrow()
                            hostScope.launch {
                                reconciler.reconcile()
                                reconciliationEvents.send(result.updated)
                            }
                        },
                    )
                },
            ) { screen ->
                clickRowContentDescriptionAction(screen.scene, targetProfile.name, "Ещё")
                clickTextAction(screen.scene, getString(Res.string.common_refresh))
                val request = awaitDeferredWithRenderTicks(screen.scene, enteredFetch, "repository-backed manual refresh")
                assertEquals(targetProfile.id, request.profile.id)
                assertEquals(targetProfile.name, request.profile.name)
                assertEquals(originalUrl, request.profile.sourceUrl)
                assertEquals(originalUrl, request.url)

                val latestTarget = normalizedProfile(
                    id = targetProfile.id,
                    name = "Latest local profile name",
                    sourceUrl = originalUrl,
                    updateLocked = false,
                    content = profileGroupDocument("Stable", listOf("Node A", "Node B")),
                ).copy(lastUpdatedAtMillis = 700L)
                val lateEdit = configRepository.transformLatest { latest ->
                    latest.copy(configs = latest.configs.map { current ->
                        if (current.id == latestTarget.id) latestTarget else current
                    })
                }
                screen.replaceLatestLibrary(lateEdit)

                clickProfileCardSelection(screen.scene, siblingProfile.name)
                val selectedLibrary = awaitChannelWithRenderTicks(
                    screen.scene,
                    transformEvents,
                    "active profile selection commit",
                )
                assertEquals(siblingProfile.id, selectedLibrary.selectedConfigId)
                assertEquals("Latest local profile name", selectedLibrary.configs.single { it.id == targetProfile.id }.name)
                val selectionReconcile = awaitChannelWithRenderTicks(
                    screen.scene,
                    reconciliationEvents,
                    "active profile selection reconciliation",
                )
                assertEquals(siblingProfile.id, selectionReconcile.selectedConfigId)
                assertEquals(siblingProfile.id, readConfigs().selectedConfigId)
                assertFalse(returnDocument.isCompleted, "The deferred profile response must still be in flight")

                returnDocument.complete(response)
                val refreshedLibrary = awaitChannelWithRenderTicks(
                    screen.scene,
                    transformEvents,
                    "manual refresh library commit",
                )
                assertEquals(siblingProfile.id, refreshedLibrary.selectedConfigId)
                assertEquals("Latest local profile name", refreshedLibrary.configs.single { it.id == targetProfile.id }.name)
                awaitChannelWithRenderTicks(
                    screen.scene,
                    reconciliationEvents,
                    "manual refresh group reconciliation",
                )

                val reloadedConfigs = readConfigs()
                assertEquals(listOf(7, 8), reloadedConfigs.configs.map(ConfigProfile::id))
                assertEquals(siblingProfile.id, reloadedConfigs.selectedConfigId)
                assertEquals(initialLibrary.nextConfigId, reloadedConfigs.nextConfigId)
                val savedTarget = reloadedConfigs.configs.single { it.id == targetProfile.id }
                assertEquals("Latest local profile name", savedTarget.name)
                assertEquals(originalUrl, savedTarget.sourceUrl)
                assertTrue(savedTarget.content.contains("Stable = select, Node B, Node C"))
                assertTrue(
                    savedTarget.content.contains("profile-name = Latest local profile name"),
                    "A response without a name retains the latest local profile name",
                )
                assertEquals(siblingProfile.content, reloadedConfigs.configs.single { it.id == siblingProfile.id }.content)

                val reloadedServers = DesktopServerLibraries.load(serverPath).getOrThrow()
                assertEquals(initialServers.selectedServerId, reloadedServers.selectedServerId)
                assertEquals(initialServers.nextServerId, reloadedServers.nextServerId)
                assertEquals(initialServers.servers.single { it.id == 99 }, reloadedServers.servers.single { it.id == 99 })
                val stable = reloadedServers.servers.single { it.id == 30 }.decode().getOrThrow() as StrategyGroup
                assertEquals(listOf(11, 12), stable.proxyServerIds)
                assertEquals(11, stable.selectedMemberId)
                assertEquals(targetProfile.id, stable.sourceTrafficConfigId)
                assertEquals("Stable", stable.sourcePolicyGroupName)
                val sibling = reloadedServers.servers.single { it.id == 25 }.decode().getOrThrow() as StrategyGroup
                assertEquals(listOf(12), sibling.proxyServerIds)
                assertEquals(12, sibling.selectedMemberId)
                assertEquals(siblingProfile.id, sibling.sourceTrafficConfigId)
            }
        } finally {
            transformEvents.close()
            reconciliationEvents.close()
        }
    }

    private data class ManualProfileFetchRequest(
        val profile: TrafficConfigState,
        val url: String,
    )

    private data class DesktopScreenLibraryTransformHooks(
        val transformLatestWithPrevious: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibraryTransformResult,
        val afterTransform: (DesktopConfigLibraryTransformResult) -> Unit,
    )

    private suspend fun withDesktopScreen(
        proxyServers: List<CatalogProxyServerRecord>,
        scriptedApplyOutcome: ProxyServerEditApplyOutcome? = null,
        targetProfile: ConfigProfile? = null,
        activeProfile: ConfigProfile? = null,
        initialLibraryOverride: DesktopConfigLibrary? = null,
        viewportWidth: Int = TestSceneWidth,
        viewportHeight: Int = TestSceneHeight,
        fetchProfileDocument: suspend (TrafficConfigState, String) -> String = { _, _ ->
            error("Unexpected manual profile refresh in this test")
        },
        libraryTransformHooksFactory: ((File, DesktopConfigLibrary, CoroutineScope) -> DesktopScreenLibraryTransformHooks)? = null,
        block: suspend (DesktopScreenHarness) -> Unit,
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
            val nextLibraryTransform = CompletableDeferred<DesktopConfigLibrary>()
            val applyRequests = mutableListOf<GlobalProxyGroupApplyRequest>()
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val libraryTransformHooks = libraryTransformHooksFactory?.invoke(root, initialLibrary, hostScope)
            val transformLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary = { transform ->
                val result = libraryTransformHooks?.transformLatestWithPrevious?.invoke(transform)
                    ?: DesktopConfigLibraryTransformResult(
                        previous = latestLibrary,
                        updated = transform(latestLibrary),
                    )
                val updated = result.updated
                libraryTransformCount++
                persistedLibrary = updated
                latestLibrary = updated
                if (result.previous.configs != updated.configs || result.previous.selectedConfigId != updated.selectedConfigId) {
                    libraryTransformHooks?.afterTransform?.invoke(result)
                }
                nextLibraryTransform.complete(updated)
                updated
            }
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
                            fetchProfileDocument = fetchProfileDocument,
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
                        replaceLatestLibrary = { latestLibrary = it },
                        nextLibraryTransform = nextLibraryTransform,
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
        val replaceLatestLibrary: (DesktopConfigLibrary) -> Unit,
        val nextLibraryTransform: Deferred<DesktopConfigLibrary>,
        val applyRequests: () -> List<GlobalProxyGroupApplyRequest>,
        val proxyServers: List<CatalogProxyServerRecord>,
    )

    private suspend fun <T> awaitChannelWithRenderTicks(
        scene: ImageComposeScene,
        channel: Channel<T>,
        description: String,
    ): T {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            val next = channel.tryReceive()
            if (next.isSuccess) return next.getOrThrow()
            render(scene)
            delay(1L)
        }
        error("$description did not complete within three seconds")
    }

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

    private fun profileGroupDocument(name: String, members: List<String>): String = """[General]
        loglevel = warning

        [Proxy Group]
        $name = select, ${members.joinToString(", ")}, skipi-display=always

        [Rule]
        FINAL,DIRECT
    """.trimIndent()

    private fun storedProfileServer(
        id: Int,
        server: features.proxy.server.model.ProxyServer<*>,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )

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

    private fun clickProfileCardSelection(scene: ImageComposeScene, profileName: String) {
        val profileCard = assertNotNull(
            rowCards(scene, profileName).firstOrNull { candidate ->
                candidate.descendants().any { it.hasContentDescription("Ещё") }
            },
            "Expected profile card '$profileName' with its More action",
        )
        clickNodeAction(scene, profileCard, "The '$profileName' profile selection card")
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

    private fun setEditableTextByCurrentValue(
        scene: ImageComposeScene,
        currentValue: String,
        newValue: String,
    ) {
        val field = assertNotNull(
            awaitNode(scene) { candidate ->
                candidate.config.contains(SemanticsActions.SetText) &&
                    candidate.config.contains(SemanticsProperties.EditableText) &&
                    candidate.config[SemanticsProperties.EditableText].text == currentValue
            },
            "Expected an editable field with the exact current value",
        )
        val setTextAction = assertNotNull(field.config[SemanticsActions.SetText].action)
        assertTrue(setTextAction(AnnotatedString(newValue)), "The matching field should accept the requested text")
        render(scene)
        assertEquals(newValue, awaitEditableText(scene, newValue))
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

    private suspend fun <T> awaitDeferredWithRenderTicks(
        scene: ImageComposeScene,
        deferred: Deferred<T>,
        description: String,
    ): T {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!deferred.isCompleted && System.nanoTime() < deadline) {
            render(scene)
            delay(1L)
        }
        assertTrue(deferred.isCompleted, "$description did not complete within three seconds")
        return withTimeout(100L) { deferred.await() }
    }

    private fun advancingSystemNanoTime(): Long {
        frameTimeNanos += 16_666_667L
        return frameTimeNanos
    }

    private suspend fun <T> withTemporaryRoot(block: suspend (File) -> T): T {
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
