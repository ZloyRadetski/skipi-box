// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.proxy_editor_chain_member
import app.skipi.ui.resources.proxy_editor_chain_member_unselected
import app.skipi.ui.resources.proxy_editor_strategy_group_all_groups
import app.skipi.ui.resources.proxy_editor_strategy_group_random
import app.skipi.ui.resources.proxy_editor_strategy_group_round_robin
import app.skipi.ui.resources.proxy_server_list_chain_proxy_summary
import app.skipi.ui.resources.proxy_server_list_strategy_group_summary
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the real Desktop Home route from its server card into the chain-member dropdown. */
class DesktopChainMemberEligibilityScreenIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun chainMemberDropdownOffersOrdinaryManualAndOrphanServersButExcludesAllCompositeServers() = runBlocking {
        val editLabel = getString(Res.string.common_edit)
        val memberOneLabel = getString(Res.string.proxy_editor_chain_member).replace("{index}", "1")
        val unselectedMemberLabel = getString(Res.string.proxy_editor_chain_member_unselected)
        val summaryTemplate = getString(Res.string.proxy_server_list_strategy_group_summary)
        val allGroupsLabel = getString(Res.string.proxy_editor_strategy_group_all_groups)
        val manualStrategyLabel = summaryTemplate
            .replace("{strategy}", getString(Res.string.proxy_editor_strategy_group_random))
            .replace("{group}", allGroupsLabel)
        val profileStrategyLabel = summaryTemplate
            .replace("{strategy}", getString(Res.string.proxy_editor_strategy_group_round_robin))
            .replace("{group}", allGroupsLabel)
        val targetChainOptionLabel = getString(Res.string.proxy_server_list_chain_proxy_summary)
            .replace("{count}", "1")

        val targetChainId = 301
        val ordinaryServerId = 302
        val manualStrategyId = 303
        val profileStrategyId = 304
        val otherChainId = 305
        val orphanServerId = 306
        val targetChainName = "Chain being edited"
        val ordinaryManualName = "Eligible manual server"
        val ordinaryManualHost = "eligible-manual.example"
        val ordinaryManualAddress = "$ordinaryManualHost:443"
        val orphanServerName = "Eligible orphan server"
        val orphanServerHost = "eligible-orphan.example"
        val orphanServerAddress = "$orphanServerHost:443"

        val serverLibrary = DesktopServerLibrary(
            selectedServerId = targetChainId,
            servers = listOf(
                stored(targetChainId, ChainProxy(remarks = targetChainName, proxyServerIds = listOf(-900))),
                stored(
                    ordinaryServerId,
                    VLESS(
                        remarks = ordinaryManualName,
                        id = "ordinary-manual-user",
                        server = ordinaryManualHost,
                        port = "443",
                        encryption = "none",
                    ),
                ),
                stored(
                    orphanServerId,
                    VLESS(
                        remarks = orphanServerName,
                        id = "orphan-server-user",
                        server = orphanServerHost,
                        port = "443",
                        encryption = "none",
                    ),
                    subscriptionId = 777,
                ),
                stored(
                    manualStrategyId,
                    StrategyGroup(
                        remarks = "Standalone manual strategy",
                        strategy = StrategyGroupConstants.TYPE_RANDOM,
                    ),
                ),
                stored(
                    profileStrategyId,
                    StrategyGroup(
                        remarks = "Profile-owned strategy",
                        strategy = StrategyGroupConstants.TYPE_ROUND_ROBIN,
                        sourceTrafficConfigId = 901,
                        sourcePolicyGroupName = "Profile policy",
                    ),
                ),
                stored(
                    otherChainId,
                    ChainProxy(
                        remarks = "Other composite chain",
                        proxyServerIds = listOf(ordinaryServerId, ordinaryServerId),
                    ),
                ),
            ),
        )

        val sceneWidth = 1500
        val sceneHeight = 1200
        val scene = ImageComposeScene(width = sceneWidth, height = sceneHeight)
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    DesktopProxyHome(
                        serverLibrary = serverLibrary,
                        subscriptionLibrary = DesktopSubscriptionLibrary(),
                        subscriptionCatalog = SubscriptionCatalog(),
                        subscriptionUpdate = null,
                        serverLink = "",
                        subscriptionUrl = "",
                        updatingSubscription = false,
                        running = false,
                        canToggleTunnel = false,
                        tunnelMessage = "",
                        serverMessage = "",
                        subscriptionMessage = "",
                        confirmDeletion = true,
                        activeProfileName = null,
                        activeTrafficConfigId = null,
                        trafficConfigContentById = emptyMap(),
                        exportFullJson = { error("Unexpected full-JSON export in the chain-member eligibility test") },
                        onSelectStrategyMember = { _, _ -> error("Unexpected strategy-member selection") },
                        latencyByServerId = emptyMap(),
                        testingServerIds = emptySet(),
                        pingingSubscriptionIds = emptySet(),
                        onServerLinkChange = { error("Unexpected server-link write") },
                        onSubscriptionUrlChange = { error("Unexpected subscription-URL write") },
                        onToggleTunnel = { error("Unexpected tunnel action") },
                        onSelectServer = { error("Unexpected server selection") },
                        onDeleteServer = { error("Unexpected server deletion") },
                        onSaveServer = { _, _ -> error("Unexpected server save") },
                        onMeasureServers = { error("Unexpected latency measurement") },
                        onLaunchHomeLatencyTest = { _, _ -> error("Unexpected latency-test launch") },
                        onUpdateSubscriptionById = { error("Unexpected subscription update") },
                        scheduledSubscriptionId = null,
                        onScheduledSubscriptionConsumed = {},
                        onPrepareSubscription = { error("Unexpected subscription preparation") },
                        onImport = { error("Unexpected import") },
                        onSaveSubscriptionGroup = { _, _, _ -> error("Unexpected subscription-group save") },
                        onSetSubscriptionEnabled = { _, _ -> error("Unexpected subscription toggle") },
                        onDeleteSubscription = { error("Unexpected subscription deletion") },
                        contentPadding = PaddingValues(),
                        onMoveGroup = { _, _ -> error("Unexpected group movement") },
                    )
                }
            }

            render(scene)
            assertNotNull(awaitVisibleText(scene, targetChainName), "The selected target chain should be shown on Home")
            clickRowAction(scene, targetChainName, editLabel)
            assertNotNull(awaitVisibleText(scene, memberOneLabel), "Editing the target should show its first hop selector")
            assertNotNull(awaitVisibleText(scene, unselectedMemberLabel), "The target's first hop should start unselected")

            clickClickableTextAction(scene, unselectedMemberLabel)
            assertNotNull(awaitVisibleText(scene, unselectedMemberLabel), "The open dropdown should show its unselected option")
            assertNotNull(awaitVisibleText(scene, ordinaryManualAddress), "The ordinary manual server should be offered as a hop")
            assertNotNull(awaitVisibleText(scene, orphanServerAddress), "An orphan server with an unknown provider ID should be offered as a hop")
            assertFalse(
                hasVisibleText(scene, targetChainOptionLabel),
                "The chain being edited must not be offered as its own hop",
            )
            assertFalse(
                hasVisibleText(scene, manualStrategyLabel),
                "A standalone manual StrategyGroup is composite and must not be offered as a hop",
            )
            assertFalse(
                hasVisibleText(scene, profileStrategyLabel),
                "A profile-owned StrategyGroup is composite and must not be offered as a hop",
            )
            assertFalse(
                hasVisibleText(scene, "$ordinaryManualName -> $ordinaryManualName"),
                "A different ChainProxy must not be offered as a hop",
            )
        } finally {
            scene.close()
        }
    }

    private fun stored(id: Int, server: ProxyServer<*>, subscriptionId: Int? = null) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )

    private fun clickRowAction(scene: ImageComposeScene, rowTitle: String, actionLabel: String) {
        val title = requireNotNull(awaitVisibleText(scene, rowTitle)) {
            "Expected Home row '$rowTitle' to be visible"
        }
        var clickable: SemanticsNode? = null
        for (rowOwner in title.ancestorsIncludingSelf()) {
            val labeledAction = rowOwner.descendants().firstOrNull { candidate ->
                candidate.hasContentDescription(actionLabel)
            } ?: continue
            var candidate: SemanticsNode? = labeledAction
            while (candidate != null) {
                if (candidate.config.contains(SemanticsActions.OnClick)) {
                    clickable = candidate
                    break
                }
                if (candidate == rowOwner) break
                candidate = candidate.parent
            }
            if (clickable != null) break
        }
        val clickAction = requireNotNull(clickable?.config?.get(SemanticsActions.OnClick)?.action) {
            "Expected the '$actionLabel' action to be inside Home row '$rowTitle'"
        }
        assertTrue(clickAction(), "The '$actionLabel' action for '$rowTitle' should succeed")
        render(scene)
    }

    private fun clickClickableTextAction(scene: ImageComposeScene, label: String) {
        val node = requireNotNull(awaitVisibleNode(scene) { candidate ->
            candidate.hasText(label) && candidate.ancestorsIncludingSelf().any { ancestor ->
                ancestor.config.contains(SemanticsActions.OnClick)
            }
        }) { "Expected the visible text '$label' to have a public OnClick action" }
        val clickable = requireNotNull(node.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        }) { "Expected '$label' to expose a public OnClick action" }
        val onClick = requireNotNull(clickable.config[SemanticsActions.OnClick].action)
        assertTrue(onClick(), "The '$label' OnClick action should succeed")
        render(scene)
    }

    private fun awaitVisibleText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitVisibleNode(scene) { candidate -> candidate.hasText(label) }

    private fun hasVisibleText(scene: ImageComposeScene, label: String): Boolean =
        awaitVisibleNode(scene, timeoutMillis = 300L) { candidate -> candidate.hasText(label) } != null

    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitVisibleNode(
        scene: ImageComposeScene,
        timeoutMillis: Long = 3_000L,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var stableFrames = 0
        var matchingNode: SemanticsNode? = null
        do {
            render(scene)
            val next = allNodes(scene).firstOrNull { candidate ->
                predicate(candidate) && isCenterOnScreen(candidate, 1500, 1200)
            }
            if (next == null) {
                stableFrames = 0
                matchingNode = null
            } else if (next.boundsInRoot == matchingNode?.boundsInRoot) {
                stableFrames += 1
            } else {
                matchingNode = next
                stableFrames = 1
            }
            if (stableFrames >= 2) return matchingNode
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
        var remaining = 10
        while (current != null && remaining > 0) {
            yield(current)
            current = current.parent
            remaining--
        }
    }

    private fun SemanticsNode.hasText(expected: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { text -> text.text.equals(expected, ignoreCase = true) }

    private fun SemanticsNode.hasContentDescription(expected: String): Boolean =
        config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .any { description -> description.equals(expected, ignoreCase = true) }

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
}
