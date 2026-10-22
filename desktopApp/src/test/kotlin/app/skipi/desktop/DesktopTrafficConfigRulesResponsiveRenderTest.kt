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
import app.skipi.app.model.ProxyServerRecord as PersistedProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.configs_name
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.configs_rules_policy
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_rules_value
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.routing_outbound_direct
import app.skipi.ui.resources.routing_system_targets
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.proxy.server.model.VLESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.compose.resources.getString
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopTrafficConfigRulesResponsiveRenderTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun rendersRulesListFormAndPolicyPickerAtNarrowAndWideDesktopViewports() = runBlocking {
        val labels = Labels(
            uiEdit = getString(Res.string.configs_ui_edit),
            scrollAnchor = getString(Res.string.configs_name),
            rulesTitle = getString(Res.string.configs_rules_title),
            addRule = getString(Res.string.configs_rules_add),
            value = getString(Res.string.configs_rules_value),
            policy = getString(Res.string.configs_rules_policy),
            systemTargets = getString(Res.string.routing_system_targets),
            direct = getString(Res.string.routing_outbound_direct),
            delete = getString(Res.string.common_delete),
        )

        renderViewport(width = 390, height = 844, labels = labels)
        renderViewport(width = 1100, height = 800, labels = labels)
    }

    private fun renderViewport(width: Int, height: Int, labels: Labels) {
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val profile = ConfigProfile(
                id = 7,
                name = "Rules render fixture",
                sourceUrl = "",
                updateLocked = false,
                lastUpdatedAtMillis = 0L,
                content = """[General]
loglevel = warning

[Rule]
DOMAIN,render-fixture.example,DIRECT
FINAL,PROXY
""",
            )
            val initialLibrary = DesktopConfigLibrary(
                selectedConfigId = profile.id,
                configs = listOf(profile),
                nextConfigId = profile.id + 1,
            )
            var latestLibrary by mutableStateOf(initialLibrary)
            val transformLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary = { transform ->
                transform(latestLibrary).also { updated -> latestLibrary = updated }
            }
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val services = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { it.id == profileId }
                        ?.toTrafficConfigState("Desktop rules responsive render test")
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
            // ImageComposeScene's backing surface is fixed when constructed, so each viewport gets a fresh scene.
            val scene = ImageComposeScene(width = width, height = height)
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
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the Rules responsive test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the Rules test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the Rules test") },
                            proxyServers = catalogProxyServers(),
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = "Desktop rules responsive render test",
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

                clickContentDescription(scene, labels.uiEdit, width, height)
                scrollUntilClickableTextFromVisibleAnchor(
                    scene = scene,
                    anchorLabel = labels.scrollAnchor,
                    targetLabel = labels.rulesTitle,
                    width = width,
                    height = height,
                )
                clickClickableText(scene, labels.rulesTitle, width, height)
                clickClickableText(scene, labels.rulesTitle, width, height)
                assertNotNull(awaitVisibleText(scene, labels.rulesTitle, width, height))
                assertNotNull(awaitVisibleText(scene, labels.addRule, width, height))
                assertNotNull(awaitVisibleContentDescription(scene, labels.delete, width, height))
                saveSnapshot(scene, "rules-list", width, height)

                clickText(scene, labels.addRule, width, height)
                assertNotNull(awaitVisibleText(scene, labels.addRule, width, height))
                assertNotNull(awaitVisibleText(scene, labels.value, width, height))
                assertNotNull(awaitVisibleText(scene, labels.policy, width, height))
                saveSnapshot(scene, "rule-form", width, height)

                clickText(scene, labels.policy, width, height)
                assertNotNull(awaitVisibleText(scene, labels.systemTargets, width, height))
                assertNotNull(awaitVisibleText(scene, labels.direct, width, height))
                saveSnapshot(scene, "policy-picker", width, height)
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    private fun clickContentDescription(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ) {
        val node = requireNotNull(awaitVisibleContentDescription(scene, label, width, height)) {
            "Expected an on-screen accessible control labeled '$label' at ${width}x$height"
        }
        click(scene, node)
    }

    private fun clickText(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val node = requireNotNull(awaitVisibleText(scene, label, width, height)) {
            "Expected rendered text '$label' on screen at ${width}x$height"
        }
        click(scene, node)
    }

    private fun clickClickableText(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val node = requireNotNull(awaitVisibleNode(scene, width, height) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) } &&
                candidate.ancestorsIncludingSelf().any { ancestor ->
                    ancestor.config.contains(SemanticsActions.OnClick)
                }
        }) {
            "Expected rendered action text '$label' with a clickable card ancestor at ${width}x$height"
        }
        val clickable = requireNotNull(
            node.ancestorsIncludingSelf().firstOrNull { candidate ->
                candidate.config.contains(SemanticsActions.OnClick)
            },
        ) { "Expected '$label' to have a clickable card ancestor" }
        val onClick = requireNotNull(clickable.config[SemanticsActions.OnClick].action) {
            "Expected '$label' to expose its public OnClick action"
        }
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun scrollUntilClickableTextFromVisibleAnchor(
        scene: ImageComposeScene,
        anchorLabel: String,
        targetLabel: String,
        width: Int,
        height: Int,
    ) {
        val anchor = requireNotNull(awaitVisibleText(scene, anchorLabel, width, height)) {
            "Expected the visible '$anchorLabel' field before scrolling to the Routing card"
        }
        val scrollableRoot = requireNotNull(
            allNodes(scene)
                .filter { candidate ->
                    candidate.config.contains(SemanticsActions.ScrollBy) &&
                        candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange)
                }
                .maxByOrNull { candidate -> candidate.boundsInRoot.width * candidate.boundsInRoot.height },
        ) {
            "Expected the Basics page's public vertical ScrollBy container; anchor bounds=${anchor.boundsInRoot}"
        }
        val scrollBy = requireNotNull(scrollableRoot.config[SemanticsActions.ScrollBy].action) {
            "Expected the Basics page's root LazyColumn to expose public ScrollBy"
        }

        val deadline = System.nanoTime() + 3_000_000_000L
        var scrollSteps = 0
        var previousTarget: Pair<Int, Rect>? = null
        var stableTargetFrames = 0
        do {
            render(scene)
            val target = allNodes(scene).firstOrNull { candidate ->
                candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .any { text -> text.text.equals(targetLabel, ignoreCase = true) } &&
                    candidate.ancestorsIncludingSelf().any { ancestor ->
                        ancestor.config.contains(SemanticsActions.OnClick)
                    } && isCenterOnScreen(candidate, width, height)
            }
            val sample = target?.let { it.id to it.boundsInRoot }
            if (sample != null && sample == previousTarget) {
                stableTargetFrames++
                if (stableTargetFrames >= 1) return
            } else {
                stableTargetFrames = 0
            }
            previousTarget = sample

            if (sample == null && scrollSteps < 12) {
                scrollBy(0f, height * 0.65f)
                scrollSteps++
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)

        assertTrue(
            hasVisibleClickableText(scene, targetLabel, width, height),
            "The Basics page's public root LazyColumn ScrollBy should bring '$targetLabel' on screen " +
                "at ${width}x$height after $scrollSteps steps; scroll root bounds=${scrollableRoot.boundsInRoot}",
        )
    }

    private fun hasVisibleClickableText(scene: ImageComposeScene, label: String, width: Int, height: Int): Boolean =
        allNodes(scene).any { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) } &&
                candidate.ancestorsIncludingSelf().any { ancestor ->
                    ancestor.config.contains(SemanticsActions.OnClick)
                } && isCenterOnScreen(candidate, width, height)
        }

    private fun awaitVisibleContentDescription(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate ->
        candidate.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .any { description -> description.equals(label, ignoreCase = true) }
    }

    private fun awaitVisibleText(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate ->
        candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { text -> text.text.equals(label, ignoreCase = true) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitVisibleNode(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
        timeoutMillis: Long = 2_000L,
        maxScrollSteps: Int = 10,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var scrollSteps = 0
        do {
            render(scene)
            allNodes(scene)
                .firstOrNull { candidate -> predicate(candidate) && isCenterOnScreen(candidate, width, height) }
                ?.let { return it }

            if (scrollSteps < maxScrollSteps) {
                val scrollNode = allNodes(scene).firstOrNull { candidate ->
                    candidate.config.contains(SemanticsActions.ScrollBy) &&
                        candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange)
                }
                val scrollAction = scrollNode?.config?.get(SemanticsActions.ScrollBy)?.action
                if (scrollAction != null) {
                    scrollAction(0f, height * 0.65f)
                    scrollSteps += 1
                }
            }
            Thread.sleep(16L)
        } while (System.nanoTime() < deadline)
        return null
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun allNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }

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

    private fun isCenterOnScreen(node: SemanticsNode, width: Int, height: Int): Boolean {
        val center = node.boundsInRoot.center
        return center.x in 0f..width.toFloat() && center.y in 0f..height.toFloat()
    }

    private fun click(scene: ImageComposeScene, node: SemanticsNode) {
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

    private fun saveSnapshot(scene: ImageComposeScene, page: String, width: Int, height: Int) {
        val image = scene.render(advancingSystemNanoTime())
        try {
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            assertTrue(!image.isEmpty, "$page at ${width}x$height should render visible content")
            val pixels = requireNotNull(image.peekPixels())
            try {
                val sampledColors = buildSet {
                    for (y in 0 until height step 24) {
                        for (x in 0 until width step 24) add(pixels.getColor(x, y))
                    }
                }
                assertTrue(sampledColors.size >= 3, "$page should render controls and cards")
            } finally {
                pixels.close()
            }

            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                val png = encoded.bytes
                assertTrue(png.size > 1_024, "$page PNG should contain the rendered screen")
                val reportDirectory = File(
                    System.getProperty("user.dir"),
                    "build/reports/desktop-traffic-config-rules-responsive",
                )
                assertTrue(reportDirectory.mkdirs() || reportDirectory.isDirectory)
                File(reportDirectory, "$page-${width}x$height.png").writeBytes(png)
            } finally {
                encoded.close()
            }
        } finally {
            image.close()
        }
    }

    private fun catalogProxyServers() = listOf(
        PersistedProxyServerRecord(
            id = 37,
            server = VLESS(
                remarks = "Responsive render outbound",
                id = "responsive-render-user",
                server = "render.example",
                port = "443",
                encryption = "none",
            ),
        ),
    )

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("desktop-traffic-rules-render-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private data class Labels(
        val uiEdit: String,
        val scrollAnchor: String,
        val rulesTitle: String,
        val addRule: String,
        val value: String,
        val policy: String,
        val systemTargets: String,
        val direct: String,
        val delete: String,
    )
}
