// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListPages
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListSearchBar
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListUserTabs
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_empty
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.ProxyAppListItem
import features.proxy.app.model.ProxyAppListUserSpaceTabUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text

/** Exercises the shared Per-App presentation with test-only rows and a host-owned icon slot. */
@OptIn(ExperimentalComposeUiApi::class)
class ProxyAppListPresentationIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun rowRendersPackageIdentityIconSlotAndTogglesEverySharedUidKey() = runBlocking {
        val packageEntry = app(
            packageName = "com.example.chat",
            label = "Chat",
            uid = 10_042,
        )
        val sharedSelectionKeys = listOf(
            "0:com.example.chat",
            "0:com.example.companion",
        )
        val selectionChanges = mutableListOf<SelectionChange>()
        val item = item(
            app = packageEntry,
            sharedUid = true,
            selectionKeys = sharedSelectionKeys,
        )
        val scene = scene {
            pages(
                itemsByUser = mapOf(0 to listOf(item)),
                tabs = listOf(tab(0, "Owner", checkedCount = 0)),
                selectedUserId = 0,
                selectedKeys = emptySet(),
                modeIndex = ProxyAppListModeWhitelist,
                onSelectionChange = { keys, checked -> selectionChanges += SelectionChange(keys, checked) },
            )
        }
        try {
            assertNotNull(awaitText(scene, "Chat"))
            assertNotNull(awaitText(scene, "com.example.chat"))
            assertNotNull(awaitText(scene, "SUID:10042"))
            assertNotNull(awaitText(scene, "icon:com.example.chat:enabled"))

            clickText(scene, "Chat")

            assertEquals(listOf(SelectionChange(sharedSelectionKeys, true)), selectionChanges)
        } finally {
            scene.close()
        }
    }

    @Test
    fun companionSelectionChecksRowAndUncheckingRemovesTheWholeSharedUidGroup() = runBlocking {
        val sharedSelectionKeys = listOf(
            "0:com.example.chat",
            "0:com.example.companion",
        )
        val selectionChanges = mutableListOf<SelectionChange>()
        val scene = scene {
            pages(
                itemsByUser = mapOf(
                    0 to listOf(
                        item(
                            app("com.example.chat", "Chat", uid = 10_042),
                            sharedUid = true,
                            selectionKeys = sharedSelectionKeys,
                        ),
                    ),
                ),
                tabs = listOf(tab(0, "Owner", checkedCount = 1)),
                selectedUserId = 0,
                selectedKeys = setOf("0:com.example.companion"),
                modeIndex = ProxyAppListModeWhitelist,
                onSelectionChange = { keys, checked -> selectionChanges += SelectionChange(keys, checked) },
            )
        }
        try {
            val checkbox = requireNotNull(awaitNode(scene) { node ->
                node.config.contains(SemanticsProperties.ToggleableState)
            }) { "A shared-UID package row should expose checkbox state" }
            assertEquals(ToggleableState.On, checkbox.config[SemanticsProperties.ToggleableState])

            val onClick = requireNotNull(checkbox.config[SemanticsActions.OnClick].action) {
                "The checked package checkbox should expose its toggle action"
            }
            assertTrue(onClick())
            render(scene)

            assertEquals(listOf(SelectionChange(sharedSelectionKeys, false)), selectionChanges)
        } finally {
            scene.close()
        }
    }

    @Test
    fun globalModeKeepsRowAndCheckboxFromChangingSelection() = runBlocking {
        val selectionChanges = mutableListOf<SelectionChange>()
        val scene = scene {
            pages(
                itemsByUser = mapOf(0 to listOf(item(app("com.example.mail", "Mail", uid = 10_111)))),
                tabs = listOf(tab(0, "Owner", checkedCount = 0)),
                selectedUserId = 0,
                selectedKeys = emptySet(),
                modeIndex = ProxyAppListModeGlobal,
                onSelectionChange = { keys, checked -> selectionChanges += SelectionChange(keys, checked) },
            )
        }
        try {
            val rowText = assertNotNull(awaitText(scene, "Mail"))
            val rowClick = rowText.ancestorsIncludingSelf().firstOrNull { node ->
                node.config.contains(SemanticsActions.OnClick)
            }?.config?.get(SemanticsActions.OnClick)?.action
            val checkbox = requireNotNull(awaitNode(scene) { node ->
                node.config.contains(SemanticsProperties.ToggleableState) &&
                    node.config.contains(SemanticsProperties.Disabled)
            }) { "The package checkbox should be disabled in global mode" }
            assertNotNull(awaitText(scene, "UID:10111"))

            rowClick?.invoke()
            checkbox.config[SemanticsActions.OnClick].action?.invoke()
            render(scene)

            assertEquals(emptyList(), selectionChanges)
        } finally {
            scene.close()
        }
    }

    @Test
    fun tabsShowCheckedCountsAndKeepPageInSyncWithSelectedUser() = runBlocking<Unit> {
        var tabs by mutableStateOf(
            listOf(
                tab(0, "Personal", checkedCount = 1),
                tab(10, "Work", checkedCount = 0),
            ),
        )
        val selectedKeys = setOf("0:com.example.personal")
        val userChanges = mutableListOf<Int>()
        var selectedUserId by mutableStateOf(0)
        val scene = scene {
            Column(Modifier.fillMaxSize()) {
                SkipiTrafficConfigProxyAppListUserTabs(
                    tabs = tabs,
                    selectedUserId = selectedUserId,
                    onSelectedUserIdChange = { userId ->
                        selectedUserId = userId
                        userChanges += userId
                    },
                )
                Box(Modifier.weight(1f)) {
                    pages(
                        itemsByUser = mapOf(
                            0 to listOf(item(app("com.example.personal", "Personal mail", userId = 0, uid = 10_001))),
                            10 to listOf(item(app("com.example.work", "Work mail", userId = 10, uid = 1_010_001))),
                        ),
                        tabs = tabs,
                        selectedUserId = selectedUserId,
                        selectedKeys = selectedKeys,
                        modeIndex = ProxyAppListModeWhitelist,
                        onSelectedUserIdChange = { userId ->
                            selectedUserId = userId
                            userChanges += userId
                        },
                    )
                }
            }
        }
        try {
            assertNotNull(awaitText(scene, "Personal (1)"))
            assertNotNull(awaitText(scene, "Work (0)"))
            assertNotNull(awaitText(scene, "Personal mail"))
            assertFalse(hasText(scene, "Work mail"), "The unselected page should not be the visible list")

            clickText(scene, "Work (0)")
            assertTrue(awaitCondition(scene) { selectedUserId == 10 })
            assertNotNull(awaitText(scene, "Work mail"))
            assertTrue(userChanges.contains(10), "Tab selection should publish its user ID")

            selectedUserId = 0
            val personalMail = requireNotNull(awaitText(scene, "Personal mail"))

            val pager = requireNotNull(personalMail.ancestorsIncludingSelf().firstOrNull { node ->
                node.config.contains(SemanticsActions.ScrollBy) &&
                    node.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
            }) { "The Personal mail page should have its existing horizontal pager semantics" }
            val scrollBy = requireNotNull(pager.config[SemanticsActions.ScrollBy].action)
            assertTrue(scrollBy(480f, 0f), "The horizontal pager should consume the scroll action")
            assertTrue(
                awaitCondition(scene) { selectedUserId == 10 },
                "A horizontal pager swipe should publish the target user's ID",
            )
            assertNotNull(awaitText(scene, "Work mail"))

            tabs = listOf(tabs.first())
            assertTrue(awaitCondition(scene) { selectedUserId == 0 })
            assertNotNull(awaitText(scene, "Personal mail"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun oneCurrentUserPageHasNoTabStripAndShowsOnlyItsRows() = runBlocking {
        val singleTab = listOf(tab(10, "Work", checkedCount = 0))
        val scene = scene {
            Column(Modifier.fillMaxSize()) {
                SkipiTrafficConfigProxyAppListUserTabs(
                    tabs = singleTab,
                    selectedUserId = 10,
                    onSelectedUserIdChange = {},
                )
                Box(Modifier.weight(1f)) {
                    pages(
                        itemsByUser = mapOf(
                            10 to listOf(item(app("com.example.work", "Work mail", userId = 10, uid = 1_010_001))),
                            0 to listOf(item(app("com.example.personal", "Personal mail", userId = 0, uid = 10_001))),
                        ),
                        tabs = singleTab,
                        selectedUserId = 10,
                        selectedKeys = emptySet(),
                        modeIndex = ProxyAppListModeWhitelist,
                    )
                }
            }
        }
        try {
            assertNotNull(awaitText(scene, "Work mail"))
            assertFalse(hasText(scene, "Personal mail"), "A current-user-only list should not expose other-user rows")
            assertFalse(hasText(scene, "Work (0)"), "A single current user should not need a tab strip")
        } finally {
            scene.close()
        }
    }

    @Test
    fun searchEmitsRawChangesImmediatelyAndDebouncesOnlyTheLatestValue() = runBlocking {
        val rawValues = mutableListOf<String>()
        val debouncedValues = mutableListOf<String>()
        var query by mutableStateOf("")
        val scene = scene {
            SkipiTrafficConfigProxyAppListSearchBar(
                query = query,
                onQueryChange = { value ->
                    query = value
                    rawValues += value
                },
                onDebouncedQueryChange = { value -> debouncedValues += value },
            )
        }
        try {
            val input = requireNotNull(awaitNode(scene) { node ->
                node.config.contains(SemanticsActions.SetText) &&
                    node.config.contains(SemanticsProperties.EditableText)
            }) { "The shared app search field should expose editable text semantics" }
            val setText = requireNotNull(input.config[SemanticsActions.SetText].action)
            assertTrue(setText(AnnotatedString("outdated")))
            render(scene)
            assertEquals(listOf("outdated"), rawValues)

            advanceFor(scene, 90L)
            val updatedInput = requireNotNull(awaitNode(scene) { node ->
                node.config.contains(SemanticsActions.SetText) &&
                    node.config.contains(SemanticsProperties.EditableText)
            })
            assertTrue(requireNotNull(updatedInput.config[SemanticsActions.SetText].action)(AnnotatedString("latest")))
            render(scene)
            assertEquals(listOf("outdated", "latest"), rawValues)

            advanceFor(scene, 100L)
            assertEquals(
                emptyList(),
                debouncedValues.filter(String::isNotEmpty),
                "The superseded query and premature latest query should not fire",
            )
            advanceFor(scene, 80L)
            assertEquals(listOf("latest"), debouncedValues.filter(String::isNotEmpty))
        } finally {
            scene.close()
        }
    }

    @Test
    fun emptyUserPageShowsLocalizedEmptyStateWithoutInventingARow() = runBlocking {
        val emptyLabel = getString(Res.string.common_empty)
        val scene = scene {
            pages(
                itemsByUser = mapOf(0 to emptyList()),
                tabs = listOf(tab(0, "Owner", checkedCount = 0)),
                selectedUserId = 0,
                selectedKeys = emptySet(),
                modeIndex = ProxyAppListModeWhitelist,
            )
        }
        try {
            assertNotNull(awaitText(scene, emptyLabel))
            assertFalse(hasText(scene, "com.example.fake"))
        } finally {
            scene.close()
        }
    }

    private fun scene(content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width = 480, height = 780)
        scene.setContent {
            ProvideSkipiTheme(
                colors = resolveBaseAppearanceColors(isDark = false),
                fontFamily = null,
                fontSizeMode = null,
                fontWeightMode = null,
                visualProfile = SkipiVisualProfile.Desktop,
            ) {
                content()
            }
        }
        render(scene)
        return scene
    }

    @Composable
    private fun pages(
        itemsByUser: Map<Int, List<ProxyAppListItem>>,
        tabs: List<ProxyAppListUserSpaceTabUi>,
        selectedUserId: Int?,
        selectedKeys: Set<String>,
        modeIndex: Int,
        onSelectedUserIdChange: (Int) -> Unit = {},
        onSelectionChange: (List<String>, Boolean) -> Unit = { _, _ -> },
    ) {
        val scrollBehavior = MiuixScrollBehavior()
        SkipiTrafficConfigProxyAppListPages(
            itemsByUser = itemsByUser,
            tabs = tabs,
            selectedUserId = selectedUserId,
            onSelectedUserIdChange = onSelectedUserIdChange,
            selectedKeys = selectedKeys,
            modeIndex = modeIndex,
            contentPadding = PaddingValues(0.dp),
            listPadding = PaddingValues(0.dp),
            scrollBehavior = scrollBehavior,
            isRefreshing = false,
            onRefresh = {},
            iconContent = { app, enabled ->
                Text("icon:${app.packageName}:${if (enabled) "enabled" else "disabled"}")
            },
            onSelectionChange = onSelectionChange,
            modifier = Modifier.fillMaxSize(),
        )
    }

    private suspend fun awaitText(scene: ImageComposeScene, text: String): SemanticsNode? =
        awaitNode(scene) { node -> node.hasText(text) }

    private suspend fun awaitCondition(scene: ImageComposeScene, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            render(scene)
            if (predicate()) return true
            delay(8L)
        }
        return false
    }

    private suspend fun awaitNode(
        scene: ImageComposeScene,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            render(scene)
            currentNodes(scene).firstOrNull(predicate)?.let { return it }
            delay(8L)
        }
        return null
    }

    private suspend fun clickText(scene: ImageComposeScene, text: String) {
        val textNode = requireNotNull(awaitText(scene, text)) { "Expected rendered text '$text'" }
        val clickable = requireNotNull(textNode.ancestorsIncludingSelf().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick)
        }) { "Expected '$text' to belong to a clickable row or tab" }
        clickNode(scene, clickable)
    }

    private fun clickNode(scene: ImageComposeScene, node: SemanticsNode) {
        val onClick = requireNotNull(node.config[SemanticsActions.OnClick].action)
        assertTrue(onClick())
        render(scene)
    }

    private suspend fun advanceFor(scene: ImageComposeScene, millis: Long) {
        val deadline = System.nanoTime() + millis * 1_000_000L
        while (System.nanoTime() < deadline) {
            render(scene)
            delay(8L)
        }
    }

    private fun render(scene: ImageComposeScene) {
        scene.render(advanceFrameTime()).close()
    }

    private fun advanceFrameTime(): Long {
        frameTimeNanos += 16_666_667L
        return frameTimeNanos
    }

    private fun currentNodes(scene: ImageComposeScene): Sequence<SemanticsNode> =
        scene.semanticsOwners.asSequence()
            .flatMap { owner -> owner.unmergedRootSemanticsNode.descendants() }

    private fun hasText(scene: ImageComposeScene, text: String): Boolean =
        currentNodes(scene).any { node -> node.hasText(text) }

    private fun SemanticsNode.hasText(text: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { item -> item.text == text }

    private fun SemanticsNode.ancestorsIncludingSelf(): Sequence<SemanticsNode> = sequence {
        var node: SemanticsNode? = this@ancestorsIncludingSelf
        var remaining = 10
        while (node != null && remaining > 0) {
            yield(node)
            node = node.parent
            remaining--
        }
    }

    private fun SemanticsNode.descendants(): Sequence<SemanticsNode> = sequence {
        yield(this@descendants)
        children.forEach { child -> yieldAll(child.descendants()) }
    }

    private fun app(
        packageName: String,
        label: String,
        userId: Int = 0,
        uid: Int,
    ) = AppPackageEntry(
        packageName = packageName,
        appLabel = label,
        userId = userId,
        uid = uid,
    )

    private fun item(
        app: AppPackageEntry,
        sharedUid: Boolean = false,
        selectionKeys: List<String> = listOf("${app.userId ?: 0}:${app.packageName}"),
    ) = ProxyAppListItem(
        key = "${app.userId ?: 0}:${app.packageName}",
        app = app,
        sharedUid = sharedUid,
        selectionKeys = selectionKeys,
    )

    private fun tab(id: Int, label: String, checkedCount: Int) = ProxyAppListUserSpaceTabUi(
        id = id,
        label = label,
        checkedCount = checkedCount,
    )

    private data class SelectionChange(
        val keys: List<String>,
        val checked: Boolean,
    )
}
