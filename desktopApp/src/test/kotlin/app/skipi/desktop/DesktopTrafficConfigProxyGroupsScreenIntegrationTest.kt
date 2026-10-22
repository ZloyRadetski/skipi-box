// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.ProxyServerRecord as PersistedProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_copy
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_more
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_proxy_groups_add
import app.skipi.ui.resources.configs_proxy_groups_title
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.deletion_confirmation_delete_proxy_server
import app.skipi.ui.resources.proxy_editor_strategy_group_select_servers
import app.skipi.ui.resources.subscription_provider_days_remaining
import app.skipi.ui.resources.subscription_provider_traffic
import app.skipi.ui.resources.subscription_provider_updated
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.analyzeShadowrocketConfig
import features.config.withSkipiSettingsInRawConfig
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.SubscriptionMetadata
import features.subscription.subscriptionTrafficSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.DateFormat
import java.nio.file.Files
import java.util.Date
import java.util.Locale
import java.util.Comparator
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TestSceneWidth = 1500f
private const val TestSceneHeight = 1600f
private const val OldMemberName = "Existing catalog member"
private const val NewMemberName = "New named catalog member"

class DesktopTrafficConfigProxyGroupsScreenIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun proxyGroupsSectionDeletesOnlyTheInactiveTargetProfileDocument() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val deleteLabel = getString(Res.string.common_delete)
        val saveLabel = getString(Res.string.common_save)
        val initialCatalog = catalogProxyServers()
        withDesktopScreen(
            targetContent = """[General]
loglevel = warning

[Proxy Group]
Remove this group = select, $OldMemberName, skipi-display=active_config
# Keep this comment beside the surviving profile entry.
Keep this group = select, $OldMemberName, skipi-display=never

[Vendor]
vendor-option = keep-me
""",
            proxyServers = initialCatalog,
        ) { screen ->
            clickContentDescription(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            assertNotNull(awaitText(screen.scene, "Remove this group"))

            clickContentDescription(screen.scene, deleteLabel)
            clickContentDescription(screen.scene, saveLabel)

            val saved = screen.persistedLibrary().configs.single { it.id == screen.targetId }
            assertEquals(
                listOf("Keep this group"),
                saved.content.analyzeShadowrocketConfig().proxyGroups.map { it.name },
            )
            assertFalse(saved.content.contains("Remove this group ="))
            assertTrue(saved.content.contains("# Keep this comment beside the surviving profile entry."))
            assertTrue(saved.content.contains("vendor-option = keep-me"))
            assertEquals(screen.initialLibrary.selectedConfigId, screen.persistedLibrary().selectedConfigId)
            assertEquals(screen.initialLibrary.nextConfigId, screen.persistedLibrary().nextConfigId)
            assertEquals(screen.sibling, screen.persistedLibrary().configs.single { it.id == screen.sibling.id })
            assertEquals(screen.active, screen.persistedLibrary().configs.single { it.id == screen.active.id })
            assertEquals(screen.target.sourceUrl, saved.sourceUrl)
            assertEquals(screen.target.updateLocked, saved.updateLocked)
            assertEquals(screen.target.lastUpdatedAtMillis, saved.lastUpdatedAtMillis)
            assertEquals(initialCatalog, screen.proxyServers, "Editing a profile proxy group must not mutate the global catalog input")
        }
    }

    @Test
    fun addingProfileGroupSavesActiveConfigLineWithoutChangingGlobalCatalog() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val addLabel = getString(Res.string.configs_proxy_groups_add)
        val saveLabel = getString(Res.string.common_save)
        val initialCatalog = catalogProxyServers()
        val newGroupName = "Desktop profile group"
        withDesktopScreen(
            targetContent = """[General]
loglevel = warning

[Vendor]
vendor-option = keep-me
""",
            proxyServers = initialCatalog,
        ) { screen ->
            clickContentDescription(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            clickTextAction(screen.scene, addLabel)
            setFirstEditableText(screen.scene, newGroupName)
            assertEquals(newGroupName, awaitEditableText(screen.scene, newGroupName))

            clickContentDescription(screen.scene, saveLabel) // Save the strategy editor's result into this profile.
            assertNotNull(awaitText(screen.scene, newGroupName), "Saving the strategy editor should return to the profile group list")
            clickContentDescription(screen.scene, saveLabel) // Save the group-list section and return to Basics.

            val saved = screen.persistedLibrary().configs.single { it.id == screen.targetId }
            val group = saved.content.analyzeShadowrocketConfig().proxyGroups.single()
            assertEquals(newGroupName, group.name)
            assertEquals("select", group.type)
            assertEquals("active_config", group.displayMode)
            assertTrue(saved.content.contains("skipi-burst-probe=false"))
            assertTrue(saved.content.contains("vendor-option = keep-me"))
            assertEquals(screen.initialLibrary.selectedConfigId, screen.persistedLibrary().selectedConfigId)
            assertEquals(screen.initialLibrary.nextConfigId, screen.persistedLibrary().nextConfigId)
            assertEquals(screen.sibling, screen.persistedLibrary().configs.single { it.id == screen.sibling.id })
            assertEquals(screen.active, screen.persistedLibrary().configs.single { it.id == screen.active.id })
            assertEquals(screen.target.sourceUrl, saved.sourceUrl)
            assertEquals(screen.target.updateLocked, saved.updateLocked)
            assertEquals(screen.target.lastUpdatedAtMillis, saved.lastUpdatedAtMillis)
            assertEquals(initialCatalog, screen.proxyServers, "Profile group creation must leave catalog records unchanged")
        }
    }

    @Test
    fun editingParsedGroupKeepsNameAndMembersWhenMemberPickerBacksOut() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val memberPickerLabel = getString(Res.string.proxy_editor_strategy_group_select_servers)
        val saveLabel = getString(Res.string.common_save)
        val initialCatalog = catalogProxyServers(
            proxyServer(37, OldMemberName),
            proxyServer(38, NewMemberName),
        )
        val renamedGroup = "Renamed profile group"
        withDesktopScreen(
            targetContent = """[General]
loglevel = warning

[Proxy Group]
Original profile group = select, $OldMemberName, skipi-display=active_config, skipi-burst-probe=false

[Vendor]
vendor-option = keep-me
""",
            proxyServers = initialCatalog,
        ) { screen ->
            clickContentDescription(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            clickTextAction(screen.scene, "Original profile group")
            setFirstEditableText(screen.scene, renamedGroup)
            assertEquals(renamedGroup, awaitEditableText(screen.scene, renamedGroup))

            clickClickableText(screen.scene, memberPickerLabel)
            assertNotNull(awaitText(screen.scene, OldMemberName))
            val canceledMemberRow = awaitText(screen.scene, NewMemberName)
            assertNotNull(canceledMemberRow)
            click(screen.scene, canceledMemberRow) // A tentative selection must be discarded by the selector's Back.
            clickTopBarBack(screen.scene) // Android's member-picker Back returns without changing its selected IDs.
            assertEquals(renamedGroup, awaitEditableText(screen.scene, renamedGroup), "The parent editor draft must survive member-picker Back")

            clickClickableText(screen.scene, memberPickerLabel)
            val oldMemberRow = awaitText(screen.scene, OldMemberName)
            assertNotNull(oldMemberRow)
            click(screen.scene, oldMemberRow) // The old member should still be selected, so this removes it.
            val newMemberRow = awaitText(screen.scene, NewMemberName)
            assertNotNull(newMemberRow)
            click(screen.scene, newMemberRow)
            assertEquals(
                screen.initialLibrary,
                screen.persistedLibrary(),
                "Changing member picks should remain a draft until the strategy editor itself is saved",
            )
            clickContentDescription(screen.scene, saveLabel) // Confirm the new ordered member list.
            assertEquals(renamedGroup, awaitEditableText(screen.scene, renamedGroup))
            assertEquals(
                screen.initialLibrary,
                screen.persistedLibrary(),
                "Confirming the nested member picker should still leave the profile document untouched",
            )
            clickContentDescription(screen.scene, saveLabel) // Return the edited group result to the profile list.
            assertNotNull(awaitText(screen.scene, groupsTitle))
            clickContentDescription(screen.scene, saveLabel) // Save the profile-group list.

            val saved = screen.persistedLibrary().configs.single { it.id == screen.targetId }
            val group = saved.content.analyzeShadowrocketConfig().proxyGroups.single()
            assertEquals(renamedGroup, group.name)
            assertEquals("select", group.type)
            assertEquals(listOf(NewMemberName), group.members)
            assertEquals("active_config", group.displayMode)
            assertFalse(saved.content.contains("Original profile group ="))
            assertTrue(saved.content.contains("vendor-option = keep-me"))
            assertEquals(screen.initialLibrary.selectedConfigId, screen.persistedLibrary().selectedConfigId)
            assertEquals(screen.initialLibrary.nextConfigId, screen.persistedLibrary().nextConfigId)
            assertEquals(screen.sibling, screen.persistedLibrary().configs.single { it.id == screen.sibling.id })
            assertEquals(screen.active, screen.persistedLibrary().configs.single { it.id == screen.active.id })
            assertEquals(screen.target.sourceUrl, saved.sourceUrl)
            assertEquals(screen.target.updateLocked, saved.updateLocked)
            assertEquals(screen.target.lastUpdatedAtMillis, saved.lastUpdatedAtMillis)
            assertEquals(initialCatalog, screen.proxyServers, "Editing profile membership must not mutate catalog records")
        }
    }

    @Test
    fun copyingEditedProfileGroupExportsItsProfileLineAndDraftWithoutPersistence() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val moreLabel = getString(Res.string.common_more)
        val copyLabel = getString(Res.string.common_copy)
        val initialCatalog = catalogProxyServers(proxyServer(5, OldMemberName))
        val exportedText = "{\"profile\":\"draft copy\"}"
        val editedName = "Uncommitted copied profile group"
        val exportedResult = AtomicReference<ProxyServerEditResult?>()
        val clipboardText = AtomicReference<String?>()
        withDesktopScreen(
            targetContent = """[General]
loglevel = warning

[Proxy Group]
Original copied group = select, $OldMemberName, skipi-display=active_config
""",
            proxyServers = initialCatalog,
            exportProxyServerFullJson = { result ->
                exportedResult.set(result)
                exportedText
            },
            copyTextToClipboard = { text -> clipboardText.set(text) },
        ) { screen ->
            clickContentDescriptionAction(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            clickTextAction(screen.scene, "Original copied group")
            setFirstEditableText(screen.scene, editedName)
            assertEquals(editedName, awaitEditableText(screen.scene, editedName))

            clickContentDescriptionAction(screen.scene, moreLabel)
            clickClickableTextAction(screen.scene, copyLabel)
            assertTrue(
                awaitCondition(screen.scene, "profile group export and clipboard callback") {
                    exportedResult.get() != null && clipboardText.get() != null
                },
                "Copy should export the edited profile-group draft and pass its text to the injected clipboard",
            )

            val result = assertNotNull(exportedResult.get())
            val expectedLineNumber = screen.target.content.analyzeShadowrocketConfig().proxyGroups
                .single { group -> group.name == "Original copied group" }
                .lineNumber
            assertTrue(expectedLineNumber > 0, "The profile group should have a positive absolute source line number")
            assertEquals(expectedLineNumber, result.serverId)
            val exportedGroup = result.server as? StrategyGroup
            assertNotNull(exportedGroup, "Copy should receive the edited StrategyGroup draft")
            assertEquals(screen.targetId, exportedGroup.sourceTrafficConfigId)
            assertEquals(editedName, exportedGroup.remarks)
            assertEquals(exportedText, clipboardText.get())
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Copy must not persist a profile edit")
            assertEquals(initialCatalog, screen.proxyServers, "Copy must not mutate the global catalog input")
        }
    }

    @Test
    fun memberPickerShowsSubscriptionOneMetadataAndLeavesCatalogsUntouched() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val memberPickerLabel = getString(Res.string.proxy_editor_strategy_group_select_servers)
        val providerTitle = "Provider profile title"
        val memberName = "Subscription one member"
        val announce = "Provider metadata announcement"
        val expiryAtSeconds = System.currentTimeMillis() / 1_000L + 30L * 24L * 60L * 60L + 12L * 60L * 60L
        val lastUpdatedAtMillis = 1_700_000_000_000L
        val expectedTraffic = getString(Res.string.subscription_provider_traffic)
            .replace("{value}", subscriptionTrafficSummary(512L, 1_024L, 2_048L).orEmpty())
        val expectedExpiry = String.format(
            Locale.getDefault(),
            getString(Res.string.subscription_provider_days_remaining),
            30,
        )
        val updatedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(lastUpdatedAtMillis))
        val expectedUpdated = getString(Res.string.subscription_provider_updated)
            .replace("{value}", updatedDate)
        val subscriptionCatalog = SubscriptionCatalog(
            subscriptions = listOf(
                SubscriptionRecord(
                    id = 1,
                    title = "Subscription one",
                    url = "https://provider-one.example/subscription",
                    userAgent = "Desktop proxy-groups metadata test",
                    updateInterval = "24",
                    hwid = "",
                    ageSecretKey = "",
                    updateViaProxy = false,
                    autoOverrideRules = true,
                    enabled = true,
                    builtIn = false,
                    metadata = SubscriptionMetadata(
                        profileTitle = providerTitle,
                        announce = announce,
                        userInfoReceived = true,
                        trafficUploadBytes = 512L,
                        trafficDownloadBytes = 1_024L,
                        trafficTotalBytes = 2_048L,
                        trafficExpireAtSeconds = expiryAtSeconds,
                    ),
                    lastUpdatedAtMillis = lastUpdatedAtMillis,
                    notifyOnExpiry = true,
                    customExpiryReminders = null,
                ),
            ),
            nextSubscriptionId = 2,
        )
        val initialServerCatalog = listOf(
            proxyServer(id = 37, remarks = memberName, sourceSubscriptionId = 1),
        )
        val targetContent = """[General]
loglevel = warning

[Proxy Group]
Metadata group = select, $memberName, skipi-display=active_config
"""

        withDesktopScreen(
            targetContent = targetContent,
            proxyServers = initialServerCatalog,
            subscriptionCatalog = subscriptionCatalog,
        ) { screen ->
            clickContentDescriptionAction(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            clickTextAction(screen.scene, "Metadata group")
            clickClickableText(screen.scene, memberPickerLabel)

            assertNotNull(awaitText(screen.scene, providerTitle))
            assertNotNull(awaitText(screen.scene, memberName))
            assertNotNull(awaitText(screen.scene, expectedTraffic), "The selector should show the provider traffic summary")
            assertNotNull(awaitText(screen.scene, expectedExpiry), "The selector should show the provider expiry summary")
            assertNotNull(awaitText(screen.scene, expectedUpdated), "The selector should show the provider update date")

            clickTextAction(screen.scene, providerTitle) // Collapse the provider header; its summary remains visible.
            assertFalse(hasVisibleText(screen.scene, announce), "The announcement should stay hidden while the provider is collapsed")
            assertNotNull(awaitText(screen.scene, expectedTraffic))
            assertNotNull(awaitText(screen.scene, expectedExpiry))
            assertNotNull(awaitText(screen.scene, expectedUpdated))
            clickTextAction(screen.scene, providerTitle) // Expand the provider header to reveal its announcement.
            assertNotNull(awaitText(screen.scene, announce))

            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Opening the member selector must not persist profile changes")
            assertEquals(initialServerCatalog, screen.proxyServers, "Opening the member selector must not mutate proxy catalog records")
            assertEquals(subscriptionCatalog, screen.subscriptionCatalog, "Opening the member selector must not mutate subscription metadata")
        }
    }

    @Test
    fun innerEditorDeleteConfirmsAndRemovesOnlyTheSelectedProfileGroup() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val moreLabel = getString(Res.string.common_more)
        val deleteLabel = getString(Res.string.common_delete)
        val cancelLabel = getString(Res.string.common_cancel)
        val confirmationTitle = getString(Res.string.deletion_confirmation_delete_proxy_server)
        val saveLabel = getString(Res.string.common_save)
        val initialCatalog = catalogProxyServers(proxyServer(5, OldMemberName))
        withDesktopScreen(
            targetContent = """[General]
loglevel = warning

[Proxy Group]
Remove from profile = select, $OldMemberName, skipi-display=active_config
Keep in profile = select, $OldMemberName, skipi-display=never
""",
            proxyServers = initialCatalog,
        ) { screen ->
            clickContentDescriptionAction(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            clickTextAction(screen.scene, "Remove from profile")
            val removedLineNumber = screen.target.content.analyzeShadowrocketConfig().proxyGroups
                .single { group -> group.name == "Remove from profile" }
                .lineNumber
            assertTrue(removedLineNumber > 0, "Editing a profile group should use its positive source line number")
            assertEquals(removedLineNumber, initialCatalog.single().id, "Fixture should collide with a global catalog ID")

            clickContentDescriptionAction(screen.scene, moreLabel)
            clickClickableTextAction(screen.scene, deleteLabel)
            assertNotNull(awaitText(screen.scene, confirmationTitle), "Deleting an existing group should ask for confirmation")
            clickClickableTextAction(screen.scene, cancelLabel)
            assertEquals(
                "Remove from profile",
                awaitEditableText(screen.scene, "Remove from profile"),
                "Cancel should keep the profile-group editor open",
            )
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Cancel must not mutate the profile library")

            clickContentDescriptionAction(screen.scene, moreLabel)
            clickClickableTextAction(screen.scene, deleteLabel)
            assertNotNull(awaitText(screen.scene, confirmationTitle))
            clickDialogTextAction(screen.scene, confirmationTitle, deleteLabel)
            assertNotNull(awaitText(screen.scene, "Keep in profile"), "Confirm should return to the profile-group list")
            clickContentDescriptionAction(screen.scene, saveLabel)

            val savedTarget = screen.persistedLibrary().configs.single { config -> config.id == screen.targetId }
            assertEquals(
                listOf("Keep in profile"),
                savedTarget.content.analyzeShadowrocketConfig().proxyGroups.map { group -> group.name },
            )
            assertFalse(savedTarget.content.contains("Remove from profile ="))
            assertEquals(screen.target.name, savedTarget.name)
            assertEquals(screen.initialLibrary.selectedConfigId, screen.persistedLibrary().selectedConfigId)
            assertEquals(screen.initialLibrary.nextConfigId, screen.persistedLibrary().nextConfigId)
            assertEquals(screen.sibling, screen.persistedLibrary().configs.single { config -> config.id == screen.sibling.id })
            assertEquals(screen.active, screen.persistedLibrary().configs.single { config -> config.id == screen.active.id })
            assertEquals(initialCatalog, screen.proxyServers, "Deleting a profile group must not mutate catalog records")
        }
    }

    @Test
    fun deletingNewDraftConfirmsThenReturnsToListWithoutPersistedChanges() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val routingTitle = getString(Res.string.configs_rules_title)
        val groupsTitle = getString(Res.string.configs_proxy_groups_title)
        val addLabel = getString(Res.string.configs_proxy_groups_add)
        val moreLabel = getString(Res.string.common_more)
        val deleteLabel = getString(Res.string.common_delete)
        val cancelLabel = getString(Res.string.common_cancel)
        val confirmationTitle = getString(Res.string.deletion_confirmation_delete_proxy_server)
        val initialCatalog = catalogProxyServers()
        val draftName = "New profile group to discard"
        withDesktopScreen(
            targetContent = """[General]
loglevel = warning
""",
            proxyServers = initialCatalog,
        ) { screen ->
            clickContentDescriptionAction(screen.scene, uiEditLabel)
            clickTextAction(screen.scene, routingTitle)
            clickTextAction(screen.scene, groupsTitle)
            clickTextAction(screen.scene, addLabel)
            setFirstEditableText(screen.scene, draftName)
            assertEquals(draftName, awaitEditableText(screen.scene, draftName))

            clickContentDescriptionAction(screen.scene, moreLabel)
            clickClickableTextAction(screen.scene, deleteLabel)
            assertNotNull(awaitText(screen.scene, confirmationTitle), "A draft with serverId -1 should follow configured deletion confirmation")
            clickClickableTextAction(screen.scene, cancelLabel)
            assertEquals(draftName, awaitEditableText(screen.scene, draftName), "Cancel should keep the new draft open")
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Cancel must leave every profile untouched")

            clickContentDescriptionAction(screen.scene, moreLabel)
            clickClickableTextAction(screen.scene, deleteLabel)
            assertNotNull(awaitText(screen.scene, confirmationTitle))
            clickDialogTextAction(screen.scene, confirmationTitle, deleteLabel)
            assertNotNull(awaitText(screen.scene, groupsTitle), "Confirming draft deletion should return to the group list")
            assertEquals(screen.initialLibrary, screen.persistedLibrary(), "Deleting draft serverId -1 should be skipped by profile persistence")
            assertEquals(initialCatalog, screen.proxyServers, "Deleting a new profile draft must not mutate catalog records")
        }
    }

    private fun withDesktopScreen(
        targetContent: String,
        proxyServers: List<PersistedProxyServerRecord>,
        subscriptionCatalog: SubscriptionCatalog = SubscriptionCatalog(),
        exportProxyServerFullJson: suspend (ProxyServerEditResult) -> String = {
            error("Unexpected proxy-server export in this test")
        },
        copyTextToClipboard: (String) -> Unit = {
            error("Unexpected clipboard write in this test")
        },
        block: (DesktopScreenHarness) -> Unit,
    ) {
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val target = profile(7, "Inactive target profile", targetContent).let { stored ->
                stored.copy(
                    content = stored.toTrafficConfigState("Desktop proxy-groups integration test")
                        .withSkipiSettingsInRawConfig()
                        .rawConfig,
                )
            }
            val sibling = profile(
                11,
                "Sibling profile",
                """[General]
loglevel = info

[Proxy Group]
Sibling profile group = select, $OldMemberName
""",
            )
            val active = profile(3, "Active profile", "[General]\nloglevel = debug\n")
            val initialLibrary = DesktopConfigLibrary(
                selectedConfigId = active.id,
                configs = listOf(target, sibling, active),
                nextConfigId = 29,
            )
            var latestLibrary by mutableStateOf(initialLibrary)
            var persistedLibrary = initialLibrary
            val transformLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary = { transform ->
                transform(latestLibrary).also { updated ->
                    persistedLibrary = updated
                    latestLibrary = updated
                }
            }
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val services = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { it.id == profileId }
                        ?.toTrafficConfigState("Desktop proxy-groups integration test")
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
            val scene = ImageComposeScene(width = TestSceneWidth.toInt(), height = TestSceneHeight.toInt())
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
                            fetchProfileDocument = { _, _ -> error("Unexpected manual profile refresh") },
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the profile proxy-groups test") },
                            proxyServers = proxyServers,
                            subscriptionCatalog = subscriptionCatalog,
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = "Desktop proxy-groups integration test",
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(confirmDeletion = true),
                            resourceServices = services,
                            resourceFileStatusMessage = "",
                            exportProxyServerFullJson = exportProxyServerFullJson,
                            copyTextToClipboard = copyTextToClipboard,
                        )
                    }
                }
                render(scene)
                block(
                    DesktopScreenHarness(
                        scene = scene,
                        initialLibrary = initialLibrary,
                        persistedLibrary = { persistedLibrary },
                        target = target,
                        sibling = sibling,
                        active = active,
                        proxyServers = proxyServers,
                        subscriptionCatalog = subscriptionCatalog,
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
        val target: ConfigProfile,
        val sibling: ConfigProfile,
        val active: ConfigProfile,
        val proxyServers: List<PersistedProxyServerRecord>,
        val subscriptionCatalog: SubscriptionCatalog,
    ) {
        val targetId: Int get() = target.id
    }

    private fun clickContentDescription(scene: ImageComposeScene, label: String) {
        val node = awaitNode(scene, description = "visible description '$label'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { description -> description.equals(label, ignoreCase = true) }
        }
        assertNotNull(node, "Expected an accessible control labeled '$label'")
        click(scene, node.nearestClickableAncestor() ?: node)
    }

    private fun clickContentDescriptionAction(scene: ImageComposeScene, label: String) {
        val node = awaitNode(scene, description = "visible description '$label'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { description -> description.equals(label, ignoreCase = true) }
        }
        assertNotNull(node, "Expected an accessible control labeled '$label'")
        val target = node.nearestClickableAncestor() ?: node
        val onClick = assertNotNull(
            target.config[SemanticsActions.OnClick].action,
            "Expected the accessible control '$label' to expose its public OnClick action",
        )
        assertTrue(onClick(), "The accessible control '$label' should accept its OnClick action")
        render(scene)
    }

    private fun clickText(scene: ImageComposeScene, label: String) {
        val node = awaitText(scene, label)
        assertNotNull(node, "Expected rendered text '$label'")
        click(scene, node)
    }

    private fun clickClickableText(scene: ImageComposeScene, label: String) {
        val node = awaitNode(scene, description = "clickable text '$label'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) } &&
                candidate.nearestClickableAncestor() != null
        }
        assertNotNull(node, "Expected rendered action text '$label'")
        click(scene, node.nearestClickableAncestor() ?: node)
    }

    private fun clickClickableTextAction(scene: ImageComposeScene, label: String) {
        val node = awaitNode(scene, description = "clickable text '$label'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) } &&
                candidate.nearestClickableAncestor() != null
        }
        assertNotNull(node, "Expected rendered action text '$label'")
        val target = assertNotNull(node.nearestClickableAncestor())
        val onClick = assertNotNull(
            target.config[SemanticsActions.OnClick].action,
            "Expected '$label' to expose its public OnClick action",
        )
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun clickDialogTextAction(scene: ImageComposeScene, dialogTitle: String, label: String) {
        val node = awaitNode(scene, description = "dialog action '$label' in '$dialogTitle'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) } &&
                candidate.nearestClickableAncestor() != null &&
                candidate.rootSemanticsNode().descendants().any { ownerNode ->
                    ownerNode.hasVisibleBounds() &&
                        ownerNode.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                            .any { text -> text.text.equals(dialogTitle, ignoreCase = true) }
                }
        }
        assertNotNull(node, "Expected dialog action text '$label' inside '$dialogTitle'")
        val target = assertNotNull(node.nearestClickableAncestor())
        val onClick = assertNotNull(
            target.config[SemanticsActions.OnClick].action,
            "The dialog '$label' action should expose its public OnClick action",
        )
        assertTrue(onClick(), "The dialog '$label' OnClick action should succeed")
        render(scene)
    }

    private fun clickTextAction(scene: ImageComposeScene, label: String) {
        val node = awaitText(scene, label)
        assertNotNull(node, "Expected rendered text '$label'")
        val target = node.nearestClickableAncestor()
        assertNotNull(target, "Expected text '$label' to have a clickable ancestor")
        val onClick = assertNotNull(
            target.config[SemanticsActions.OnClick].action,
            "Expected '$label' to expose its public OnClick action",
        )
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun setFirstEditableText(scene: ImageComposeScene, value: String) {
        val field = awaitNode(scene, description = "visible strategy-group remarks field") { candidate ->
            candidate.config.contains(SemanticsActions.SetText) && candidate.config.contains(SemanticsProperties.EditableText)
        }
        assertNotNull(field, "Expected the strategy-group remarks field to expose SetText semantics")
        val setTextAction = assertNotNull(
            field.config[SemanticsActions.SetText].action,
            "The remarks field's SetText action should be available",
        )
        assertTrue(setTextAction(AnnotatedString(value)), "The remarks field should accept test input")
        render(scene)
    }

    private fun awaitEditableText(scene: ImageComposeScene, expectedValue: String): String? {
        val field = awaitNode(scene, description = "editable text '$expectedValue'") { candidate ->
            candidate.config.contains(SemanticsProperties.EditableText) &&
                candidate.config[SemanticsProperties.EditableText].text == expectedValue
        }
        return field?.config?.get(SemanticsProperties.EditableText)?.text
    }

    private fun clickTopBarBack(scene: ImageComposeScene) {
        val back = awaitNode(scene, description = "laid-out top-left Back control") { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) &&
                center.x in 1f..72f && center.y in 1f..80f
        }
        assertNotNull(back, "Expected the current full-screen editor's top-left Back control")
        click(scene, back)
    }

    private fun awaitText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene, description = "visible text '$label'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) }
        }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun hasVisibleText(scene: ImageComposeScene, label: String): Boolean =
        scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
            .any { candidate ->
                candidate.hasVisibleBounds() &&
                    candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                        .any { text -> text.text.equals(label, ignoreCase = true) }
            }

    private fun awaitCondition(scene: ImageComposeScene, description: String, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + 3_000_000_000L
        do {
            render(scene)
            if (condition()) return true
            Thread.sleep(1L)
        } while (System.nanoTime() < deadline)
        System.err.println("Timed out waiting for $description. Visible semantics:\n${scene.semanticsDump()}")
        return false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitNode(
        scene: ImageComposeScene,
        description: String,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        var previousSample: SemanticsNodeSample? = null
        var stableFrames = 0
        repeat(120) {
            render(scene)
            val candidate = scene.semanticsOwners.asSequence()
                .flatMapIndexed { ownerIndex, owner ->
                    owner.unmergedRootSemanticsNode.descendants().map { node -> ownerIndex to node }
                }
                .firstOrNull { (_, node) -> node.hasVisibleBounds() && predicate(node) }
            val sample = candidate?.let { (ownerIndex, node) ->
                SemanticsNodeSample(ownerIndex, node.id, node.boundsInRoot)
            }
            if (sample != null && sample == previousSample) {
                stableFrames++
                if (stableFrames >= 1) return candidate?.second
            } else {
                stableFrames = 0
            }
            previousSample = sample
            Thread.sleep(1L)
        }
        System.err.println("Timed out waiting for $description. Visible semantics:\n${scene.semanticsDump()}")
        return null
    }

    private data class SemanticsNodeSample(
        val ownerIndex: Int,
        val nodeId: Int,
        val bounds: Rect,
    )

    private fun SemanticsNode.hasVisibleBounds(): Boolean {
        val bounds = boundsInRoot
        return bounds.width > 0f && bounds.height > 0f &&
            bounds.left >= 0f && bounds.top >= 0f &&
            bounds.right <= TestSceneWidth && bounds.bottom <= TestSceneHeight
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.semanticsDump(): String = semanticsOwners.asSequence()
        .flatMapIndexed { ownerIndex, owner ->
            owner.unmergedRootSemanticsNode.descendants().map { node -> ownerIndex to node }
        }
        .take(32)
        .joinToString("\n") { (ownerIndex, node) ->
            val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .joinToString(" | ") { it.text }
            val contentDescription = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .joinToString(" | ")
            val editableText = node.config.takeIf { it.contains(SemanticsProperties.EditableText) }
                ?.get(SemanticsProperties.EditableText)
                ?.text
            "owner=$ownerIndex id=${node.id} bounds=${node.boundsInRoot} " +
                "click=${node.config.contains(SemanticsActions.OnClick)} " +
                "text='${text.take(100)}' description='${contentDescription.take(100)}' " +
                "editable='${editableText?.take(100).orEmpty()}'"
        }
        .take(4_000)

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { child -> yieldAll(child.descendants()) }
    }

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

    private fun SemanticsNode.rootSemanticsNode(): SemanticsNode {
        var root = this
        while (root.parent != null) root = root.parent!!
        return root
    }

    private fun click(scene: ImageComposeScene, node: SemanticsNode) {
        assertTrue(
            node.hasVisibleBounds(),
            "Refusing to click a node without stable on-screen bounds. Visible semantics:\n${scene.semanticsDump()}",
        )
        val position: Offset = node.boundsInRoot.center
        scene.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = position,
            buttons = PointerButtons(),
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = position,
            buttons = PointerButtons(isPrimaryPressed = true),
            button = PointerButton.Primary,
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = position,
            buttons = PointerButtons(),
            button = PointerButton.Primary,
        )
        render(scene)
    }

    private fun render(scene: ImageComposeScene) {
        scene.render(advancingSystemNanoTime()).close()
    }

    private fun advancingSystemNanoTime(): Long {
        frameTimeNanos += 16_666_667L
        return frameTimeNanos
    }

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("desktop-traffic-proxy-groups-screen-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<java.nio.file.Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private fun profile(
        id: Int,
        name: String,
        content: String,
        sourceUrl: String = "https://example.test/$id.conf",
    ) = ConfigProfile(
        id = id,
        name = name,
        sourceUrl = sourceUrl,
        updateLocked = true,
        lastUpdatedAtMillis = 456L,
        content = content,
    )

    private fun catalogProxyServers(vararg servers: PersistedProxyServerRecord) =
        if (servers.isEmpty()) listOf(proxyServer(37, "Default catalog member")) else servers.toList()

    private fun proxyServer(id: Int, remarks: String, sourceSubscriptionId: Int? = null) = PersistedProxyServerRecord(
        id = id,
        sourceSubscriptionId = sourceSubscriptionId,
        server = VLESS(
            remarks = remarks,
            id = "catalog-fixture-user-$id",
            server = "fixture-$id.example",
            port = "443",
            encryption = "none",
        ),
    )
}
