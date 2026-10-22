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
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.config.TrafficConfigRuleTypes
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_close
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_rules_type
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.routing_suggestions_geosite
import app.skipi.ui.resources.routing_suggestions_geoip
import app.skipi.ui.resources.routing_suggestions_search_hint
import app.skipi.ui.resources.routing_suggestions_title
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.withSkipiSettingsInRawConfig
import features.resources.ResourceFileSourceLoyalsoldierGithub
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopTrafficConfigRuleAssetPickerScreenIntegrationTest {
    private companion object {
        const val SCENE_WIDTH = 1500
        const val SCENE_HEIGHT = 1600
        const val POINTER_SCROLL_STEP = 120f
        const val MAX_POINTER_SCROLL_STEPS = 12
        const val STABLE_POINTER_BOUNDS_FRAMES = 2
    }

    @Test
    fun ruleAssetPickersInsertAndroidValuesFromTheInactiveProfilesOwnCustomDatFile() = runBlocking {
        val uiEditLabel = getString(Res.string.configs_ui_edit)
        val rulesTitle = getString(Res.string.configs_rules_title)
        val addRuleLabel = getString(Res.string.configs_rules_add)
        val typeLabel = getString(Res.string.configs_rules_type)
        val geositePickerLabel = getString(Res.string.routing_suggestions_geosite)
        val geoipPickerLabel = getString(Res.string.routing_suggestions_geoip)
        val pickerSearchHint = getString(Res.string.routing_suggestions_search_hint)
        val ruleSetPickerLabel = getString(Res.string.routing_suggestions_title)
        val closeLabel = getString(Res.string.common_close)
        val saveLabel = getString(Res.string.common_save)

        withTemporaryRoot { root ->
            val targetCustomFile = customFile(id = 9, name = "TargetCustom.dat")
            val activeCustomFile = customFile(id = 10, name = "TargetCustom.dat")
            val target = profile(
                id = 7,
                name = "Inactive target",
                content = """[General]
loglevel = warning

[Rule]
FINAL,PROXY
""",
                customFiles = listOf(targetCustomFile),
            )
            val sibling = profile(
                id = 11,
                name = "Sibling profile",
                content = "[General]\nloglevel = info\n",
            )
            val active = profile(
                id = 3,
                name = "Active profile",
                content = "[General]\nloglevel = debug\n",
                customFiles = listOf(activeCustomFile),
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
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val targetState = target.toTrafficConfigState("Desktop rule asset integration test")
            val activeState = active.toTrafficConfigState("Desktop rule asset integration test")
            writeGeoDat(
                resourceFiles.snapshot(targetState).file(targetCustomFile),
                "target-site",
                "target-ip",
            )
            writeGeoDat(
                resourceFiles.snapshot(activeState).file(activeCustomFile),
                "active-only-site",
                "active-only-ip",
            )
            val services = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { it.id == profileId }
                        ?.toTrafficConfigState("Desktop rule asset integration test")
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
            val scene = ImageComposeScene(width = SCENE_WIDTH, height = SCENE_HEIGHT)
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
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the Rules asset test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the Rules test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the Rules test") },
                            fetchUserAgent = "Desktop rule asset integration test",
                            fetchTimeoutSeconds = 5,
                            contentPadding = PaddingValues(),
                            resourceActionScope = hostScope,
                            onResourceFileStatusMessage = {},
                            desktopSettings = DesktopAppSettings(),
                            resourceServices = services,
                            resourceFileStatusMessage = "",
                            proxyServers = emptyList(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                        )
                    }
                }
                render(scene)

                // The target profile is first in library order, while another profile remains selected.
                clickContentDescription(scene, uiEditLabel)
                clickClickableText(scene, rulesTitle)
                clickClickableText(scene, rulesTitle)

                addRuleAndSelectSuggestion(
                    scene = scene,
                    addRuleLabel = addRuleLabel,
                    typeLabel = typeLabel,
                    selectedType = "DOMAIN-SET",
                    pickerLabel = geositePickerLabel,
                    customFileGroup = "TargetCustom.dat",
                    selectedTag = "target-site",
                    expectedFieldValue = "ext:TargetCustom.dat:target-site",
                    pickerOnlyLabels = listOf(pickerSearchHint, closeLabel),
                )
                clickTopBarBack(scene, rulesTitle)

                addRuleAndSelectSuggestion(
                    scene = scene,
                    addRuleLabel = addRuleLabel,
                    typeLabel = typeLabel,
                    selectedType = "GEOIP",
                    pickerLabel = geoipPickerLabel,
                    customFileGroup = "TargetCustom.dat",
                    selectedTag = "target-ip",
                    expectedFieldValue = "target-ip",
                    pickerOnlyLabels = listOf(pickerSearchHint, closeLabel),
                )
                clickTopBarBack(scene, rulesTitle)

                addRuleAndSelectSuggestion(
                    scene = scene,
                    addRuleLabel = addRuleLabel,
                    typeLabel = typeLabel,
                    selectedType = "RULE-SET",
                    pickerLabel = ruleSetPickerLabel,
                    customFileGroup = "TargetCustom.dat",
                    selectedTag = "target-site",
                    expectedFieldValue = "ext:TargetCustom.dat:target-site",
                    pickerOnlyLabels = listOf(pickerSearchHint, closeLabel),
                )
                clickTopBarBack(scene, rulesTitle)
                clickContentDescription(scene, saveLabel)

                val savedTarget = persistedLibrary.configs.single { it.id == target.id }
                assertEquals(
                    listOf(
                        "DOMAIN-SET,ext:TargetCustom.dat:target-site,PROXY",
                        "GEOIP,target-ip,PROXY",
                        "RULE-SET,ext:TargetCustom.dat:target-site,PROXY",
                        "FINAL,PROXY",
                    ),
                    savedTarget.content.analyzeShadowrocketConfig().rules.map { it.raw },
                )
                assertTrue(savedTarget.content.contains("resource-custom-file = 9,TargetCustom.dat"))
                assertEquals(active.id, persistedLibrary.selectedConfigId)
                assertEquals(active, persistedLibrary.configs.single { it.id == active.id })
                assertEquals(sibling, persistedLibrary.configs.single { it.id == sibling.id })
                assertFalse(savedTarget.content.contains("active-only-site"))
                assertFalse(savedTarget.content.contains("active-only-ip"))
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    private fun addRuleAndSelectSuggestion(
        scene: ImageComposeScene,
        addRuleLabel: String,
        typeLabel: String,
        selectedType: String,
        pickerLabel: String,
        customFileGroup: String,
        selectedTag: String,
        expectedFieldValue: String,
        pickerOnlyLabels: List<String>,
    ) {
        clickText(scene, addRuleLabel)
        clickText(scene, typeLabel)
        clickRuleTypeOption(scene, selectedType)
        awaitRuleTypeDropdownClosed(scene, selectedType)
        clickText(scene, pickerLabel)
        clickText(scene, customFileGroup)
        val suggestion = awaitPointerText(scene, selectedTag)
        assertNotNull(suggestion, "Expected profile-scoped custom asset '$selectedTag' in the picker")
        click(scene, suggestion)
        assertEquals(expectedFieldValue, awaitEditableText(scene, expectedFieldValue))
        awaitAssetPickerDismissed(scene, customFileGroup, selectedTag, pickerOnlyLabels)
    }

    private fun clickContentDescription(scene: ImageComposeScene, label: String) {
        val node = awaitPointerNode(scene) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { description -> description.equals(label, ignoreCase = true) }
        }
        assertNotNull(node, "Expected an accessible control labeled '$label'")
        click(scene, node)
    }

    private fun clickText(scene: ImageComposeScene, label: String) {
        val node = awaitPointerText(scene, label)
        if (node == null) {
            val semanticDump = describeTextSemantics(scene)
            val screenshot = runCatching { saveDiagnosticScreenshot(scene) }.getOrNull()
            throw AssertionError(
                buildString {
                    append("Expected rendered text '").append(label).append("'.")
                    append("\nText, content-description, and editable-text semantics:")
                    append("\n").append(semanticDump)
                    if (screenshot != null) append("\nRendered screenshot: ").append(screenshot)
                },
            )
        }
        click(scene, node)
    }

    private fun clickClickableText(scene: ImageComposeScene, label: String) {
        val node = awaitNode(scene) { candidate ->
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

    private fun clickRuleTypeOption(scene: ImageComposeScene, selectedType: String) {
        val option = awaitPointerText(scene, selectedType)
        assertNotNull(option, "Expected a visible rule type option '$selectedType'")
        val clickable = option.ancestorsIncludingSelf()
            .firstOrNull { candidate -> candidate.config.contains(SemanticsActions.OnClick) }
        assertNotNull(clickable, "Expected rule type option '$selectedType' to expose an accessible click action")
        val onClick = clickable.config.get(SemanticsActions.OnClick).action
        assertNotNull(onClick, "Expected rule type option '$selectedType' to provide an accessible click action")
        assertTrue(onClick(), "Expected rule type option '$selectedType' click action to be handled")
        render(scene)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun describeTextSemantics(scene: ImageComposeScene): String =
        scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
            .mapNotNull { node ->
                val labels = buildList {
                    if (node.config.contains(SemanticsProperties.Text)) {
                        add("Text=${node.config[SemanticsProperties.Text].joinToString { it.text }}")
                    }
                    if (node.config.contains(SemanticsProperties.ContentDescription)) {
                        add("ContentDescription=${node.config[SemanticsProperties.ContentDescription].joinToString()}")
                    }
                    if (node.config.contains(SemanticsProperties.EditableText)) {
                        add("EditableText=${node.config[SemanticsProperties.EditableText].text}")
                    }
                }
                labels.takeIf { it.isNotEmpty() }?.let { "${node.boundsInRoot}: ${it.joinToString("; ")}" }
            }
            .joinToString(separator = "\n")
            .ifEmpty { "<no text-like semantics found>" }

    private fun saveDiagnosticScreenshot(scene: ImageComposeScene): String {
        val image = scene.render(System.nanoTime())
        try {
            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Rendered scene should encode as PNG"
            }
            try {
                val reportDirectory = File(
                    System.getProperty("user.dir"),
                    "build/reports/desktop-rule-asset-failure",
                )
                check(reportDirectory.mkdirs() || reportDirectory.isDirectory) {
                    "Could not create screenshot directory: $reportDirectory"
                }
                val output = File(reportDirectory, "missing-text.png")
                output.writeBytes(encoded.bytes)
                return output.absolutePath
            } finally {
                encoded.close()
            }
        } finally {
            image.close()
        }
    }

    private fun awaitEditableText(scene: ImageComposeScene, expectedValue: String): String? {
        val field = awaitNode(scene) { candidate ->
            candidate.config.contains(SemanticsProperties.EditableText) &&
                candidate.config[SemanticsProperties.EditableText].text == expectedValue
        }
        return field?.config?.get(SemanticsProperties.EditableText)?.text
    }

    private fun clickTopBarBack(scene: ImageComposeScene, rulesTitle: String) {
        val back = awaitPointerNode(scene) { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) &&
                center.x in 1f..72f && center.y in 1f..80f
        }
        assertNotNull(back, "Expected the current full-screen editor's top-left Back control")
        click(scene, back)
        assertNotNull(
            awaitPointerText(scene, rulesTitle),
            "Expected the Rules list to be ready after leaving the rule form",
        )
    }

    private fun awaitText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) }
        }

    private fun awaitPointerText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitPointerNode(scene) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text == label }
        }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitPointerNode(
        scene: ImageComposeScene,
        timeoutMillis: Long = 2_000L,
        maxScrollSteps: Int = MAX_POINTER_SCROLL_STEPS,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var scrollSteps = 0
        var previousBounds: Rect? = null
        var stableBoundsFrames = 0
        do {
            render(scene)
            val matches = allNodes(scene).filter(predicate).toList()
            val visible = matches.firstOrNull { candidate -> candidate.boundsInRoot.isPointerReady() }
            if (visible != null) {
                val bounds = visible.boundsInRoot
                if (previousBounds?.approximatelyEquals(bounds) == true) {
                    stableBoundsFrames += 1
                } else {
                    previousBounds = bounds
                    stableBoundsFrames = 1
                }
                if (stableBoundsFrames >= STABLE_POINTER_BOUNDS_FRAMES) return visible
            } else {
                previousBounds = null
                stableBoundsFrames = 0
                if (scrollSteps < maxScrollSteps) {
                    val scrollNode = matches.asSequence()
                        .flatMap { node -> node.ancestorsIncludingSelf() }
                        .firstOrNull { candidate ->
                            candidate.config.contains(SemanticsActions.ScrollBy) &&
                                (candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
                                    candidate.config.contains(SemanticsProperties.HorizontalScrollAxisRange))
                    }
                    if (scrollNode != null) {
                        val scrollAction = scrollNode.config.get(SemanticsActions.ScrollBy).action
                        if (scrollAction != null) {
                            if (scrollNode.config.contains(SemanticsProperties.VerticalScrollAxisRange)) {
                                scrollAction(0f, POINTER_SCROLL_STEP)
                            } else {
                                scrollAction(POINTER_SCROLL_STEP, 0f)
                            }
                            scrollSteps += 1
                        }
                    }
                }
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)
        return null
    }

    private fun awaitRuleTypeDropdownClosed(scene: ImageComposeScene, expectedType: String) {
        val startedAtNanos = System.nanoTime()
        val deadline = System.nanoTime() + 2_000L * 1_000_000L
        var previousBounds: Rect? = null
        var stableBoundsFrames = 0
        var renderedFrames = 0
        var lastTypeSummary = "<no rule type semantics observed>"
        do {
            render(scene)
            renderedFrames += 1
            val typeNodes = allNodes(scene)
                .flatMap { candidate ->
                    candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                        .asSequence()
                        .map { text -> candidate to text.text }
                }
                .filter { (_, text) -> text in TrafficConfigRuleTypes }
                .toList()
            lastTypeSummary = typeNodes.take(24).joinToString(separator = "; ") { (node, text) ->
                "'$text' bounds=${node.boundsInRoot} pointerReady=${node.boundsInRoot.isPointerReady()}"
            }.ifEmpty { "<no rule type semantics observed>" }
            val selected = typeNodes.singleOrNull { (_, text) -> text == expectedType }
            if (typeNodes.size == 1 && selected != null && selected.first.boundsInRoot.isPointerReady()) {
                val bounds = selected.first.boundsInRoot
                if (previousBounds?.approximatelyEquals(bounds) == true) {
                    stableBoundsFrames += 1
                } else {
                    previousBounds = bounds
                    stableBoundsFrames = 1
                }
                if (stableBoundsFrames >= STABLE_POINTER_BOUNDS_FRAMES) return
            } else {
                previousBounds = null
                stableBoundsFrames = 0
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)

        val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
        throw AssertionError(
            "Expected the rule type dropdown to close with '$expectedType' selected after " +
                "$elapsedMillis ms and $renderedFrames rendered frames; last rule type semantics: " +
                lastTypeSummary,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitAssetPickerDismissed(
        scene: ImageComposeScene,
        customFileGroup: String,
        selectedTag: String,
        pickerOnlyLabels: List<String>,
    ) {
        val dismissalLabels = setOf(customFileGroup, selectedTag) + pickerOnlyLabels
        val deadline = System.nanoTime() + 2_000L * 1_000_000L
        var absentFrames = 0
        do {
            render(scene)
            val pickerSemanticsRemain = allNodes(scene).any { candidate ->
                candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .any { text -> text.text in dismissalLabels }
            }
            if (pickerSemanticsRemain) {
                absentFrames = 0
            } else {
                absentFrames += 1
                if (absentFrames >= STABLE_POINTER_BOUNDS_FRAMES) return
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)

        throw AssertionError("Expected the selected asset picker to finish dismissing")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun allNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }

    private fun SemanticsNode.ancestorsIncludingSelf(): Sequence<SemanticsNode> = sequence {
        var current: SemanticsNode? = this@ancestorsIncludingSelf
        while (current != null) {
            yield(current)
            current = current.parent
        }
    }

    private fun Rect.isPointerReady(): Boolean =
        width > 1f && height > 1f &&
            left >= 0f && top >= 0f &&
            right <= SCENE_WIDTH.toFloat() && bottom <= SCENE_HEIGHT.toFloat()

    private fun Rect.approximatelyEquals(other: Rect): Boolean =
        abs(left - other.left) < 0.5f &&
            abs(top - other.top) < 0.5f &&
            abs(right - other.right) < 0.5f &&
            abs(bottom - other.bottom) < 0.5f

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitNode(
        scene: ImageComposeScene,
        timeoutMillis: Long = 2_000L,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        do {
            render(scene)
            scene.semanticsOwners.asSequence()
                .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }
                .firstOrNull(predicate)
                ?.let { return it }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)
        return null
    }

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { child -> yieldAll(child.descendants()) }
    }

    private fun click(scene: ImageComposeScene, node: SemanticsNode) {
        val position: Offset = node.boundsInRoot.center
        assertTrue(node.boundsInRoot.isPointerReady(), "Pointer target must have an on-screen hitbox")
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
        scene.render(System.nanoTime()).close()
    }

    private fun writeGeoDat(file: File, vararg tags: String) {
        file.parentFile?.mkdirs()
        file.writeBytes(encodeGeoDat(*tags))
    }

    private fun encodeGeoDat(vararg tags: String): ByteArray = buildList {
        tags.forEach { tag ->
            val tagBytes = tag.encodeToByteArray()
            val entry = byteArrayOf(0x0a, tagBytes.size.toByte()) + tagBytes
            add(0x0a.toByte())
            add(entry.size.toByte())
            addAll(entry.asList())
        }
    }.toByteArray()

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("desktop-rule-assets-screen-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<java.nio.file.Path>()).forEach { path ->
                    Files.deleteIfExists(path)
                }
            }
        }
    }

    private fun profile(
        id: Int,
        name: String,
        content: String,
        customFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ConfigProfile {
        val state = TrafficConfigState(
            id = id,
            name = name,
            rawConfig = content,
            sourceUrl = "https://example.test/$id.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 456L,
            resourceSettings = TrafficConfigResourceSettings(
                source = ResourceFileSourceLoyalsoldierGithub,
                customFiles = customFiles,
            ),
        ).withSkipiSettingsInRawConfig()
        return ConfigProfile(
            id = id,
            name = name,
            sourceUrl = state.sourceUrl,
            updateLocked = state.updateLocked,
            lastUpdatedAtMillis = state.lastUpdatedAtMillis,
            content = state.rawConfig,
        )
    }

    private fun customFile(id: Int, name: String) = TrafficConfigCustomResourceFile(
        id = id,
        name = name,
        url = "https://example.test/$id.dat",
    )
}
