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
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.ProxyServerRecord as PersistedProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_proxy_groups_add
import app.skipi.ui.resources.configs_proxy_groups_title
import app.skipi.ui.resources.configs_resources
import app.skipi.ui.resources.configs_rules_title
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.proxy_editor_remarks
import app.skipi.ui.resources.proxy_editor_strategy_group_select_servers
import app.skipi.ui.resources.proxy_editor_strategy_group_title
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.withSkipiSettingsInRawConfig
import features.proxy.server.model.VLESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTrafficConfigProxyGroupsResponsiveRenderTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun rendersProxyGroupPagesAtNarrowAndWideDesktopViewports() = kotlinx.coroutines.runBlocking {
        val labels = Labels(
            uiEdit = getString(Res.string.configs_ui_edit),
            routingTitle = getString(Res.string.configs_rules_title),
            resourcesTitle = getString(Res.string.configs_resources),
            groupsTitle = getString(Res.string.configs_proxy_groups_title),
            addGroup = getString(Res.string.configs_proxy_groups_add),
            groupEditorTitle = getString(Res.string.proxy_editor_strategy_group_title),
            groupNameField = getString(Res.string.proxy_editor_remarks),
            members = getString(Res.string.proxy_editor_strategy_group_select_servers),
            memberName = "Responsive render outbound",
            save = getString(Res.string.common_save),
            groupName = "Responsive render group",
        )

        renderViewport(width = 390, height = 844, labels = labels)
        renderViewport(width = 1100, height = 800, labels = labels)
    }

    private fun renderViewport(width: Int, height: Int, labels: Labels) {
        withTemporaryRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
            val userAgent = "Desktop proxy-groups responsive render test"
            val target = ConfigProfile(
                id = 7,
                name = "Proxy groups responsive render profile",
                sourceUrl = "",
                updateLocked = false,
                lastUpdatedAtMillis = 0L,
                content = """[General]
loglevel = warning

[Proxy Group]
${labels.groupName} = select, ${labels.memberName}, skipi-display=active_config
""",
            ).let { profile ->
                profile.copy(
                    content = profile.toTrafficConfigState(userAgent)
                        .withSkipiSettingsInRawConfig()
                        .rawConfig,
                )
            }
            val initialLibrary = DesktopConfigLibrary(
                selectedConfigId = target.id,
                configs = listOf(target),
                nextConfigId = target.id + 1,
            )
            var latestLibrary by mutableStateOf(initialLibrary)
            val transformLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary = { transform ->
                transform(latestLibrary).also { updated -> latestLibrary = updated }
            }
            val catalog = catalogProxyServers(labels.memberName)
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val services = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { it.id == profileId }?.toTrafficConfigState(userAgent)
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
            // ImageComposeScene keeps its backing size for its lifetime; use a fresh scene per viewport.
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
                            fetchProfileDocument = { _, _ -> error("Unexpected manual profile refresh") },
                            onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global proxy-group result apply in the profile proxy-groups responsive test") },
                            exportProxyServerFullJson = { error("Unexpected proxy-server export in the responsive render test") },
                            copyTextToClipboard = { error("Unexpected clipboard write in the responsive render test") },
                            proxyServers = catalog,
                            subscriptionCatalog = SubscriptionCatalog(),
                            resourceFiles = resourceFiles,
                            latencyByServerId = emptyMap(),
                            fetchUserAgent = userAgent,
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

                clickContentDescriptionAction(scene, labels.uiEdit, width, height)
                clickClickableTextAction(scene, labels.routingTitle, width, height)
                assertVisibleText(scene, labels.resourcesTitle, width, height)
                assertVisibleText(scene, labels.groupsTitle, width, height)
                assertVisibleText(scene, labels.routingTitle, width, height)
                saveSnapshot(scene, "routing", width, height)

                clickClickableTextAction(scene, labels.groupsTitle, width, height)
                assertVisibleText(scene, labels.groupsTitle, width, height)
                assertVisibleText(scene, labels.addGroup, width, height)
                assertVisibleText(scene, labels.groupName, width, height)
                saveSnapshot(scene, "groups-list", width, height)

                clickClickableTextAction(scene, labels.groupName, width, height)
                assertVisibleText(scene, labels.groupEditorTitle, width, height)
                assertVisibleText(scene, labels.groupNameField, width, height)
                assertVisibleText(scene, labels.members, width, height)
                assertVisibleContentDescription(scene, labels.save, width, height)
                saveSnapshot(scene, "group-form", width, height)

                clickClickableTextAction(scene, labels.members, width, height)
                assertVisibleText(scene, labels.members, width, height)
                assertVisibleText(scene, labels.memberName, width, height)
                assertVisibleContentDescription(scene, labels.save, width, height)
                saveSnapshot(scene, "member-selector", width, height)
            } finally {
                scene.close()
                hostScope.cancel()
            }
        }
    }

    private fun clickContentDescriptionAction(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ) {
        val node = requireNotNull(awaitVisibleContentDescription(scene, label, width, height)) {
            "Expected an on-screen accessible control labeled '$label' at ${width}x$height"
        }
        val clickable = requireNotNull(node.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        }) { "Expected '$label' to expose a public OnClick action" }
        val onClick = requireNotNull(clickable.config[SemanticsActions.OnClick].action)
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun clickClickableTextAction(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        val node = requireNotNull(awaitVisibleNode(scene, width, height) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) } &&
                candidate.ancestorsIncludingSelf().any { ancestor ->
                    ancestor.config.contains(SemanticsActions.OnClick)
                }
        }) { "Expected rendered action text '$label' with a clickable ancestor at ${width}x$height" }
        val clickable = requireNotNull(node.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        }) { "Expected '$label' to expose a public OnClick action" }
        val onClick = requireNotNull(clickable.config[SemanticsActions.OnClick].action)
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun assertVisibleText(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        requireNotNull(awaitVisibleText(scene, label, width, height)) {
            "Expected rendered text '$label' to be on screen at ${width}x$height"
        }
    }

    private fun assertVisibleContentDescription(scene: ImageComposeScene, label: String, width: Int, height: Int) {
        requireNotNull(awaitVisibleContentDescription(scene, label, width, height)) {
            "Expected accessible control '$label' to be on screen at ${width}x$height"
        }
    }

    private fun awaitVisibleText(scene: ImageComposeScene, label: String, width: Int, height: Int): SemanticsNode? =
        awaitVisibleNode(scene, width, height) { candidate ->
            candidate.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { text -> text.text.equals(label, ignoreCase = true) }
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
                val reportDirectory = File(
                    System.getProperty("user.dir"),
                    "build/reports/desktop-traffic-config-proxy-groups-responsive",
                )
                assertTrue(reportDirectory.mkdirs() || reportDirectory.isDirectory)
                val file = File(reportDirectory, "$page-${width}x$height.png")
                file.writeBytes(encoded.bytes)
                assertTrue(file.length() > 1_024L, "$page PNG should contain the rendered screen")
            } finally {
                encoded.close()
            }
        } finally {
            image.close()
        }
    }

    private fun catalogProxyServers(remarks: String) = listOf(
        PersistedProxyServerRecord(
            id = 37,
            server = VLESS(
                remarks = remarks,
                id = "responsive-render-user",
                server = "render.example",
                port = "443",
                encryption = "none",
            ),
        ),
    )

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("desktop-traffic-proxy-groups-render-").toFile()
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
        val routingTitle: String,
        val resourcesTitle: String,
        val groupsTitle: String,
        val addGroup: String,
        val groupEditorTitle: String,
        val groupNameField: String,
        val members: String,
        val memberName: String,
        val save: String,
        val groupName: String,
    )
}
