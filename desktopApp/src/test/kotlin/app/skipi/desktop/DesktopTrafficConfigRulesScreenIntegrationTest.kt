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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.ProxyServerRecord as PersistedProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.configs_rules_policy
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_rules_value
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.routing_outbound_block
import app.skipi.ui.resources.routing_outbound_direct
import app.skipi.ui.resources.routing_system_targets
import app.skipi.ui.resources.subscription_default_group
import features.config.ConfigProfile
import features.config.analyzeShadowrocketConfig
import features.config.withSkipiSettingsInRawConfig
import features.proxy.server.model.VLESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CatalogOutboundServerName = "Catalog outbound fixture"
private const val TestSceneWidth = 1500f
private const val TestSceneHeight = 1600f

class DesktopTrafficConfigRulesScreenIntegrationTest {
    @Test
    fun profileRulesSectionOpensFromVisualEditorAndDeletingARulePersistsOnlyItsTargetDocument() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val rulesTitle = getString(Res.string.configs_rules_title)
        val deleteLabel = getString(Res.string.common_delete)
        val saveLabel = getString(Res.string.common_save)
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val target = profile(
                id = 7,
                name = "Rule target",
                sourceUrl = "",
                content = """[General]
loglevel = warning

[Proxy]
server = target.example:443

[Rule]
DOMAIN-SUFFIX,delete.example,PROXY
# this comment stays between the surviving row and fallback
DOMAIN,keep.example,DIRECT
FINAL,PROXY

[Vendor]
vendor-option = keep-me
""",
            )
            val sibling = profile(
                id = 11,
                name = "Sibling profile",
                content = """[General]
loglevel = info

[Rule]
DOMAIN,sibling.example,DIRECT
FINAL,PROXY
""",
            )
            val active = profile(
                id = 3,
                name = "Active profile",
                content = "[General]\nloglevel = debug\n",
            )
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
                        ?.toTrafficConfigState("Desktop rules integration test")
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
            val scene = ImageComposeScene(width = 1500, height = 1600)
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
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the Rules test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the Rules test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the Rules test") },
                            proxyServers = catalogProxyServers(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = "Desktop rules integration test",
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(),
                            resourceServices = services,
                            resourceFileStatusMessage = "",
                        )
                    }
                }
                render(scene)

                // The profiles follow library order, so the first visible UI-edit action opens id 7.
                clickContentDescription(scene, uiEditLabel)
                val rulesSection = awaitText(scene, rulesTitle)
                assertNotNull(
                    rulesSection,
                    "Desktop's visual profile editor should expose the profile's routing-rules section",
                )
                clickClickableText(scene, rulesTitle)
                clickClickableText(scene, rulesTitle)

                clickContentDescription(scene, deleteLabel)
                clickContentDescription(scene, saveLabel)

                val savedTarget = persistedLibrary.configs.single { it.id == target.id }
                assertEquals(
                    listOf("DOMAIN,keep.example,DIRECT", "FINAL,PROXY"),
                    savedTarget.content.analyzeShadowrocketConfig().rules.map { it.raw },
                )
                assertFalse(savedTarget.content.contains("DOMAIN-SUFFIX,delete.example,PROXY"))
                assertTrue(savedTarget.content.contains("# this comment stays between the surviving row and fallback"))
                assertTrue(savedTarget.content.contains("vendor-option = keep-me"))
                assertEquals(active.id, persistedLibrary.selectedConfigId)
                assertEquals(initialLibrary.nextConfigId, persistedLibrary.nextConfigId)
                assertEquals(sibling, persistedLibrary.configs.single { it.id == sibling.id })
                assertEquals(active, persistedLibrary.configs.single { it.id == active.id })
                assertEquals(target.name, savedTarget.name)
                assertEquals(target.sourceUrl, savedTarget.sourceUrl)
                assertEquals(target.updateLocked, savedTarget.updateLocked)
                assertEquals(target.lastUpdatedAtMillis, savedTarget.lastUpdatedAtMillis)
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    @Test
    fun newRuleKeepsTypedValueWhenOutboundPickerIsCanceledAndConfirmsRejectPolicy() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val rulesTitle = getString(Res.string.configs_rules_title)
        val addRuleLabel = getString(Res.string.configs_rules_add)
        val policyLabel = getString(Res.string.configs_rules_policy)
        val systemTargetsLabel = getString(Res.string.routing_system_targets)
        val directLabel = getString(Res.string.routing_outbound_direct)
        val rejectOptionLabel = getString(Res.string.routing_outbound_block)
        val deleteLabel = getString(Res.string.common_delete)
        val saveLabel = getString(Res.string.common_save)
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val target = profile(
                id = 7,
                name = "Rule target",
                sourceUrl = "",
                content = """[General]
loglevel = warning

[Rule]
FINAL,PROXY

[Vendor]
vendor-option = keep-me
""",
            )
            val sibling = profile(11, "Sibling profile", "[General]\nloglevel = info\n")
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
                        ?.toTrafficConfigState("Desktop rules integration test")
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
            val scene = ImageComposeScene(width = 1500, height = 1600)
            val typedValue = "typed-before-policy.example"
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
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the Rules test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the Rules test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the Rules test") },
                            proxyServers = catalogProxyServers(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = "Desktop rules integration test",
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(),
                            resourceServices = services,
                            resourceFileStatusMessage = "",
                        )
                    }
                }
                render(scene)

                clickContentDescription(scene, uiEditLabel)
                clickClickableText(scene, rulesTitle)
                clickClickableText(scene, rulesTitle)
                clickText(scene, addRuleLabel)
                setEditableText(scene, typedValue)
                assertEquals(typedValue, awaitEditableText(scene, typedValue))

                clickText(scene, policyLabel)
                assertTrue(awaitText(scene, systemTargetsLabel) != null, "Policy selection should use the shared outbound-selector groups")
                clickText(scene, directLabel)
                clickTopBarBack(scene) // The selector's Back discards its pending choice.
                assertEquals(typedValue, awaitEditableText(scene, typedValue))
                assertTrue(awaitText(scene, "PROXY") != null, "Canceling the picker must keep the original policy")

                clickText(scene, policyLabel)
                assertTrue(awaitText(scene, systemTargetsLabel) != null, "Policy selection should use the shared outbound-selector groups")
                clickText(scene, rejectOptionLabel)
                clickContentDescription(scene, saveLabel) // Confirm the selector's REJECT tag.
                assertEquals(typedValue, awaitEditableText(scene, typedValue))
                assertTrue(awaitText(scene, "REJECT") != null, "Confirming the picker should update only the policy")

                clickTopBarBack(scene) // The rule form's Back saves the draft, following Android behavior.
                assertNotNull(awaitText(scene, rulesTitle), "Returning from the rule form should show the Rules list")
                clickContentDescription(scene, saveLabel) // Return from the Rules list to Basics.

                val savedTarget = persistedLibrary.configs.single { it.id == target.id }
                assertEquals(
                    listOf("DOMAIN-SUFFIX,$typedValue,REJECT", "FINAL,PROXY"),
                    savedTarget.content.analyzeShadowrocketConfig().rules.map { it.raw },
                )
                assertTrue(savedTarget.content.contains("vendor-option = keep-me"))
                assertEquals(active.id, persistedLibrary.selectedConfigId)
                assertEquals(initialLibrary.nextConfigId, persistedLibrary.nextConfigId)
                assertEquals(sibling, persistedLibrary.configs.single { it.id == sibling.id })
                assertEquals(active, persistedLibrary.configs.single { it.id == active.id })
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    @Test
    fun policyPickerShowsAndPersistsNamedServerFromDesktopCatalog() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val rulesTitle = getString(Res.string.configs_rules_title)
        val addRuleLabel = getString(Res.string.configs_rules_add)
        val policyLabel = getString(Res.string.configs_rules_policy)
        val manualServersLabel = getString(Res.string.subscription_default_group)
        val saveLabel = getString(Res.string.common_save)
        val ruleValue = "catalog-route.example"
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val target = profile(
                id = 7,
                name = "Rule target",
                sourceUrl = "",
                content = """[General]
loglevel = warning

[Rule]
FINAL,PROXY

[Vendor]
vendor-option = keep-me
""",
            )
            val sibling = profile(11, "Sibling profile", "[General]\nloglevel = info\n")
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
                        ?.toTrafficConfigState("Desktop rules integration test")
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
            val scene = ImageComposeScene(width = 1500, height = 1600)
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
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the Rules test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the Rules test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the Rules test") },
                            proxyServers = catalogProxyServers(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = "Desktop rules integration test",
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(),
                            resourceServices = services,
                            resourceFileStatusMessage = "",
                        )
                    }
                }
                render(scene)

                clickContentDescription(scene, uiEditLabel)
                clickClickableText(scene, rulesTitle)
                clickClickableText(scene, rulesTitle)
                clickText(scene, addRuleLabel)
                setEditableText(scene, ruleValue)
                assertEquals(ruleValue, awaitEditableText(scene, ruleValue))

                clickText(scene, policyLabel)
                assertTrue(awaitText(scene, manualServersLabel) != null)
                clickText(scene, manualServersLabel)
                val serverRow = awaitText(scene, CatalogOutboundServerName)
                assertNotNull(
                    serverRow,
                    "The shared policy selector should show the named VLESS server supplied by Desktop's catalog",
                )
                click(scene, serverRow)
                clickContentDescription(scene, saveLabel)
                assertTrue(
                    awaitText(scene, CatalogOutboundServerName) != null,
                    "Confirming the server should use its remarks as the Shadowrocket policy tag",
                )

                clickTopBarBack(scene) // The rule form's Back saves the draft, following Android behavior.
                assertNotNull(awaitText(scene, rulesTitle), "Returning from the rule form should show the Rules list")
                clickContentDescription(scene, saveLabel) // Return from the Rules list to Basics.

                val savedTarget = persistedLibrary.configs.single { it.id == target.id }
                assertEquals(
                    listOf("DOMAIN-SUFFIX,$ruleValue,$CatalogOutboundServerName", "FINAL,PROXY"),
                    savedTarget.content.analyzeShadowrocketConfig().rules.map { it.raw },
                )
                assertTrue(savedTarget.content.contains("vendor-option = keep-me"))
                assertEquals(active.id, persistedLibrary.selectedConfigId)
                assertEquals(initialLibrary.nextConfigId, persistedLibrary.nextConfigId)
                assertEquals(sibling, persistedLibrary.configs.single { it.id == sibling.id })
                assertEquals(active, persistedLibrary.configs.single { it.id == active.id })
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    @Test
    fun failedRuleSaveKeepsTypedFormOpenAndShowsStorageError(): Unit = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val rulesTitle = getString(Res.string.configs_rules_title)
        val addRuleLabel = getString(Res.string.configs_rules_add)
        val failureMessage = "disk write rejected"
        val typedValue = "unsaved-rule.example"
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val target = profile(
                id = 7,
                name = "Rule target",
                sourceUrl = "",
                content = """[General]
loglevel = warning

[Rule]
FINAL,PROXY
""",
            ).let { stored ->
                stored.copy(
                    content = stored.toTrafficConfigState()
                        .withSkipiSettingsInRawConfig()
                        .rawConfig,
                )
            }
            val sibling = profile(11, "Sibling profile", "[General]\nloglevel = info\n")
            val active = profile(3, "Active profile", "[General]\nloglevel = debug\n")
            val initialLibrary = DesktopConfigLibrary(
                selectedConfigId = active.id,
                configs = listOf(target, sibling, active),
                nextConfigId = 29,
            )
            var latestLibrary by mutableStateOf(initialLibrary)
            var persistedLibrary = initialLibrary
            var saveAttempts = 0
            val storageFailure = IllegalStateException(failureMessage)
            val transformLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary = { _ ->
                saveAttempts += 1
                throw storageFailure
            }
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val services = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { it.id == profileId }
                        ?.toTrafficConfigState("Desktop rules integration test")
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
            val scene = ImageComposeScene(width = 1500, height = 1600)
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
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the Rules test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the Rules test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the Rules test") },
                            proxyServers = catalogProxyServers(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = "Desktop rules integration test",
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(),
                            resourceServices = services,
                            resourceFileStatusMessage = "",
                        )
                    }
                }
                render(scene)

                clickContentDescription(scene, uiEditLabel)
                clickClickableText(scene, rulesTitle)
                clickClickableText(scene, rulesTitle)
                clickText(scene, addRuleLabel)
                setEditableText(scene, typedValue)
                assertEquals(typedValue, awaitEditableText(scene, typedValue))

                clickTopBarBack(scene) // The form attempts to save before leaving.

                assertEquals(1, saveAttempts, "Backing out of the rule form should attempt one host save")
                assertEquals(initialLibrary, persistedLibrary, "A rejected save must leave persisted data unchanged")
                assertEquals(typedValue, awaitEditableText(scene, typedValue))
                assertNotNull(
                    awaitTextContaining(scene, failureMessage),
                    "The failed host write should be surfaced while keeping the rule form open",
                )
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    private fun clickContentDescription(scene: ImageComposeScene, label: String) {
        val node = awaitNode(scene, description = "visible clickable '$label'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { description -> description.equals(label, ignoreCase = true) }
        }
        assertNotNull(node, "Expected an accessible control labeled '$label'")
        click(scene, node)
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
                candidate.ancestorsIncludingSelf().any { ancestor ->
                    ancestor.config.contains(SemanticsActions.OnClick)
                }
        }
        assertNotNull(node, "Expected rendered action text '$label'")
        val clickable = assertNotNull(
            node.ancestorsIncludingSelf().firstOrNull { candidate ->
                candidate.config.contains(SemanticsActions.OnClick)
            },
            "Expected '$label' to have a clickable card ancestor",
        )
        val onClick = assertNotNull(
            clickable.config[SemanticsActions.OnClick].action,
            "Expected '$label' to expose its public OnClick action",
        )
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun setEditableText(scene: ImageComposeScene, value: String) {
        val field = awaitNode(scene, description = "visible rule-value field") { candidate ->
            candidate.config.contains(SemanticsActions.SetText)
        }
        assertNotNull(field, "Expected the rule-value field to expose the standard SetText semantics action")
        val setTextAction = assertNotNull(
            field.config[SemanticsActions.SetText].action,
            "The rule-value field's SetText action should be available",
        )
        val applied = setTextAction(AnnotatedString(value))
        assertTrue(applied, "The rule-value field should accept test input through its semantics action")
        render(scene)
    }

    private fun awaitEditableText(scene: ImageComposeScene, expectedValue: String): String? {
        val field = awaitNode(scene, description = "visible editable text '$expectedValue'") { candidate ->
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

    private fun awaitTextContaining(scene: ImageComposeScene, value: String): SemanticsNode? =
        awaitNode(scene, description = "visible text containing '$value'") { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> value in text.text }
        }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitNode(
        scene: ImageComposeScene,
        timeoutMillis: Long = 2_000L,
        description: String = "matching visible semantics node",
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var previousSample: SemanticsNodeSample? = null
        var stableFrames = 0
        do {
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
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)
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

    private fun SemanticsNode.ancestorsIncludingSelf(): Sequence<SemanticsNode> = sequence {
        var current: SemanticsNode? = this@ancestorsIncludingSelf
        var remaining = 8
        while (current != null && remaining > 0) {
            yield(current)
            current = current.parent
            remaining--
        }
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
        scene.render().close()
    }

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("desktop-traffic-rules-screen-").toFile()
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

    private fun catalogProxyServers() = listOf(
        PersistedProxyServerRecord(
            id = 37,
            server = VLESS(
                remarks = CatalogOutboundServerName,
                id = "catalog-fixture-user",
                server = "fixture.example",
                port = "443",
                encryption = "none",
            ),
        ),
    )

}
