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
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_export_to_clipboard
import app.skipi.ui.resources.common_import_from_clipboard
import app.skipi.ui.resources.common_merge_import
import app.skipi.ui.resources.common_replace_existing
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_per_app
import app.skipi.ui.resources.configs_per_app_desktop_notice
import app.skipi.ui.resources.configs_ui_edit
import app.skipi.ui.resources.proxy_app_list_import_clipboard_message
import app.skipi.ui.resources.proxy_app_list_import_clipboard_title
import app.skipi.ui.resources.proxy_app_list_import_invalid_user
import app.skipi.ui.resources.proxy_app_list_imported
import app.skipi.ui.resources.proxy_app_list_mode
import app.skipi.ui.resources.proxy_app_list_mode_global
import app.skipi.ui.resources.proxy_app_list_mode_whitelist
import app.skipi.ui.resources.proxy_app_list_more_actions
import app.skipi.ui.resources.proxy_app_list_search_label
import app.skipi.ui.resources.proxy_app_list_show_system_apps
import app.skipi.ui.resources.proxy_app_list_title
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.skipiSectionValues
import features.config.withSkipiSettingsInRawConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat

/** Verifies the Desktop Per-App profile route, immediate persistence, clipboard flow, and viewports. */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopTrafficConfigPerAppScreenIntegrationTest {
    private var frameTimeNanos = 1_000_000_000L

    @Test
    fun moreUiEditOpensInactivePerAppAndBackDoesNotWriteAgain() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        withScreen(fixture, labels, 390, 844) { screen ->
            openPerApp(screen, labels)
            assertEquals(0, screen.libraryTransformCount())
            assertFalse(hasText(screen.scene, labels.save), "Per-App has no Save draft action")
            assertFalse(hasContentDescription(screen.scene, labels.save), "Per-App has no Save action in the toolbar")

            clickContentDescription(screen.scene, labels.modeAction)
            clickTextAction(screen.scene, labels.whitelistMode)

            val saved = awaitPersistedProfile(screen, fixture.target.id) { profile ->
                profile.toTrafficConfigState(ResourceUserAgent).proxyAppListMode == ProxyAppListModeWhitelist
            }
            assertEquals(ProxyAppListModeWhitelist, saved.toTrafficConfigState(ResourceUserAgent).proxyAppListMode)
            assertEquals(
                fixture.target.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps,
                saved.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps,
            )
            assertPerAppDocument(
                saved,
                expectedMode = "whitelist",
                expectedSelectedApps = fixture.target.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps,
            )
            assertTargetEnvelopeAndSections(
                saved,
                fixture.target,
                proxyServer = "target.example:443",
                rule = "DOMAIN,target.example,DIRECT",
                vendorValue = "target-vendor",
            )
            assertLibraryEnvelope(screen.persistedLibrary(), fixture.library)
            assertEquals(1, screen.libraryTransformCount(), "Changing mode should commit immediately once")

            clickTopBarBack(screen.scene, 390, 844)
            val basicsProfileName = awaitEditableText(screen.scene, labels.targetName)
            val basicsDiagnostic = if (basicsProfileName == null) {
                captureBackNavigationDiagnostics(screen.scene, 390, 844)
            } else {
                null
            }
            assertNotNull(
                basicsProfileName,
                basicsDiagnostic?.let { "Per-App Back should return to Basics; $it" }
                    ?: "Per-App Back should return to Basics",
            )
            scrollBasicsToVisibleSection(screen, labels.perAppSection)
            val basicsSection = awaitVisibleClickableText(screen.scene, labels.perAppSection, 390, 844)
            val backDiagnostic = if (basicsSection == null) {
                captureBackNavigationDiagnostics(screen.scene, 390, 844)
            } else {
                null
            }
            assertNotNull(
                basicsSection,
                backDiagnostic?.let { "Per-App Back should return to Basics; $it" }
                    ?: "Per-App Back should return to Basics",
            )
            assertEquals(1, screen.libraryTransformCount(), "Returning to Basics should not write or roll back")
            assertEquals(
                ProxyAppListModeWhitelist,
                screen.persistedLibrary().configs.single { it.id == fixture.target.id }
                    .toTrafficConfigState(ResourceUserAgent).proxyAppListMode,
            )
        }
    }

    @Test
    fun replaceClipboardImportUsesDesktopUserIdFallbackAndReportsTwoImports() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        val clipboardText = AtomicReference<String?>("mode=whitelist\ncom.example.bare\n10:com.example.other")
        val readCount = AtomicInteger()
        withScreen(
            fixture = fixture,
            labels = labels,
            width = 1100,
            height = 800,
            readClipboard = { readCount.incrementAndGet(); clipboardText.get() },
        ) { screen ->
            openPerApp(screen, labels)
            openImportDialog(screen.scene, labels)
            assertNotNull(awaitVisibleText(screen.scene, labels.importCount(2), 1100, 800))

            clickTextAction(screen.scene, labels.replace)
            assertTrue(awaitVisibleText(screen.scene, labels.importedCount(2), 1100, 800) != null)

            val savedLibrary = awaitLibrary(screen) { library ->
                val target = library.configs.single { it.id == fixture.target.id }
                target.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps ==
                    listOf("0:com.example.bare", "10:com.example.other")
            }
            val savedTarget = savedLibrary.configs.single { it.id == fixture.target.id }
            val state = savedTarget.toTrafficConfigState(ResourceUserAgent)
            assertEquals(ProxyAppListModeWhitelist, state.proxyAppListMode)
            assertEquals(listOf("0:com.example.bare", "10:com.example.other"), state.proxyAppListSelectedApps)
            assertPerAppDocument(
                savedTarget,
                expectedMode = "whitelist",
                expectedSelectedApps = listOf("0:com.example.bare", "10:com.example.other"),
            )
            assertTargetEnvelopeAndSections(
                savedTarget,
                fixture.target,
                proxyServer = "target.example:443",
                rule = "DOMAIN,target.example,DIRECT",
                vendorValue = "target-vendor",
            )
            assertLibraryEnvelope(savedLibrary, fixture.library)
            assertEquals(1, readCount.get())
            assertEquals(1, screen.libraryTransformCount())
        }
    }

    @Test
    fun mergeAfterDialogUsesLatestRowAndReportsNoNewIds() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        val clipboardText = AtomicReference<String?>(
            "mode=global\n0:com.example.existing\n0:com.example.imported",
        )
        val readCount = AtomicInteger()
        withScreen(
            fixture = fixture,
            labels = labels,
            width = 1100,
            height = 800,
            readClipboard = { readCount.incrementAndGet(); clipboardText.get() },
        ) { screen ->
            openPerApp(screen, labels)
            openImportDialog(screen.scene, labels)
            assertNotNull(awaitVisibleText(screen.scene, labels.importCount(2), 1100, 800))

            val latestTarget = profile(
                id = fixture.target.id,
                name = "Late target edit",
                sourceUrl = "https://latest.example/target.conf",
                updateLocked = true,
                lastUpdatedAtMillis = 987L,
                mode = ProxyAppListModeWhitelist,
                selectedApps = listOf(
                    "0:com.example.existing",
                    "0:com.example.imported",
                    "10:com.example.late",
                ),
                proxyServer = "latest-target.example:8443",
                rule = "DOMAIN,latest-target.example,DIRECT",
                vendorValue = "late-target-vendor",
            )
            val lateSibling = profile(
                id = LateSiblingId,
                name = "Late sibling",
                sourceUrl = "https://late.example/sibling.conf",
                updateLocked = false,
                lastUpdatedAtMillis = 123L,
                selectedApps = listOf("legacy.late-sibling"),
                proxyServer = "late-sibling.example:443",
                rule = "DOMAIN,late-sibling.example,PROXY",
                vendorValue = "late-sibling-vendor",
            )
            val lateLibrary = fixture.library.copy(
                selectedConfigId = fixture.sibling.id,
                configs = listOf(latestTarget, fixture.sibling, lateSibling),
                nextConfigId = LateNextConfigId,
            )
            screen.persistExternal(lateLibrary)
            assertEquals(lateLibrary, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount(), "External latest-row setup is not a UI action")
            assertNotNull(
                awaitVisibleText(screen.scene, labels.importTitle, 1100, 800),
                "The pending clipboard import should remain open after the late profile edit",
            )

            clickTextAction(screen.scene, labels.merge)
            assertTrue(awaitVisibleText(screen.scene, labels.importedCount(0), 1100, 800) != null)

            val saved = awaitLibrary(screen) { library ->
                library.configs.singleOrNull { it.id == fixture.target.id }
                    ?.toTrafficConfigState(ResourceUserAgent)
                    ?.proxyAppListSelectedApps == latestTarget.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps
            }
            assertEquals(fixture.sibling.id, saved.selectedConfigId)
            assertEquals(LateNextConfigId, saved.nextConfigId)
            assertEquals(listOf(fixture.target.id, fixture.sibling.id, LateSiblingId), saved.configs.map(ConfigProfile::id))
            assertEquals(fixture.sibling, saved.configs.single { it.id == fixture.sibling.id })
            assertEquals(lateSibling, saved.configs.single { it.id == LateSiblingId })

            val savedTarget = saved.configs.single { it.id == fixture.target.id }
            val state = savedTarget.toTrafficConfigState(ResourceUserAgent)
            assertEquals(latestTarget.name, savedTarget.name)
            assertEquals(latestTarget.sourceUrl, savedTarget.sourceUrl)
            assertEquals(latestTarget.updateLocked, savedTarget.updateLocked)
            assertEquals(latestTarget.lastUpdatedAtMillis, savedTarget.lastUpdatedAtMillis)
            assertEquals(ProxyAppListModeGlobal, state.proxyAppListMode)
            assertEquals(
                listOf("0:com.example.existing", "0:com.example.imported", "10:com.example.late"),
                state.proxyAppListSelectedApps,
            )
            assertPerAppDocument(
                savedTarget,
                expectedMode = "global",
                expectedSelectedApps = listOf("0:com.example.existing", "0:com.example.imported", "10:com.example.late"),
            )
            assertTargetEnvelopeAndSections(
                savedTarget,
                latestTarget,
                proxyServer = "latest-target.example:8443",
                rule = "DOMAIN,latest-target.example,DIRECT",
                vendorValue = "late-target-vendor",
            )
            assertEquals(1, readCount.get())
            assertEquals(1, screen.libraryTransformCount())
        }
    }

    @Test
    fun mergeAfterTargetDisappearsClosesDialogAndReportsZeroWithoutResurrection() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        withScreen(
            fixture = fixture,
            labels = labels,
            width = 1100,
            height = 800,
            readClipboard = { "mode=global\n0:com.example.imported" },
        ) { screen ->
            openPerApp(screen, labels)
            openImportDialog(screen.scene, labels)
            assertNotNull(awaitVisibleText(screen.scene, labels.importCount(1), 1100, 800))

            val libraryAfterExternalRemoval = fixture.library.copy(configs = listOf(fixture.sibling))
            screen.removeTargetExternallyWithoutPublishing(libraryAfterExternalRemoval)
            assertEquals(libraryAfterExternalRemoval, screen.persistedLibrary())
            assertEquals(fixture.library, screen.displayedLibrary(), "The current screen keeps its stale profile snapshot until the confirmation action")
            assertNotNull(awaitVisibleText(screen.scene, labels.importTitle, 1100, 800), "The pending import remains open after an external file update")

            clickTextAction(screen.scene, labels.merge)

            assertTrue(
                awaitVisibleText(screen.scene, labels.importedCount(0), 1100, 800) != null,
                "The missing-profile outcome should report Imported 0 in the catalog status",
            )
            assertTrue(awaitVisibleText(screen.scene, labels.siblingName, 1100, 800) != null, "The latest profile catalog should be shown")
            assertFalse(hasText(screen.scene, labels.importTitle), "The merge confirmation should close after the missing-profile result")
            assertFalse(hasText(screen.scene, labels.targetName), "The catalog must not recreate the removed profile")
            assertEquals(libraryAfterExternalRemoval, screen.persistedLibrary())
            assertEquals(libraryAfterExternalRemoval, screen.displayedLibrary())
            assertEquals(fixture.sibling.id, screen.persistedLibrary().selectedConfigId)
            assertEquals(fixture.library.nextConfigId, screen.persistedLibrary().nextConfigId)
        }
    }

    @Test
    fun failedMergeAndReplaceKeepImportDialogOpenWithoutSuccessOrPersistence() = runBlocking {
        val labels = labels()
        val importedApps = listOf("0:com.example.imported", "10:com.example.other")
        for (confirmAction in listOf(labels.merge, labels.replace)) {
            val fixture = fixture()
            val readCount = AtomicInteger()
            val writeCount = AtomicInteger()
            withScreen(
                fixture = fixture,
                labels = labels,
                width = 1100,
                height = 800,
                readClipboard = {
                    readCount.incrementAndGet()
                    "mode=global\n0:com.example.imported\n10:com.example.other"
                },
                writeClipboard = { writeCount.incrementAndGet() },
            ) { screen ->
                openPerApp(screen, labels)
                openImportDialog(screen.scene, labels)
                assertNotNull(awaitVisibleText(screen.scene, labels.importCount(2), 1100, 800))
                screen.failNextRepositorySave(IllegalStateException("injected per-app save failure"))

                clickTextAction(screen.scene, confirmAction)

                assertNotNull(
                    awaitVisibleText(screen.scene, labels.importTitle, 1100, 800),
                    "The import-mode dialog should remain open when the profile save fails",
                )
                assertNotNull(awaitVisibleText(screen.scene, labels.importCount(2), 1100, 800))
                assertFalse(hasText(screen.scene, labels.importedCount(0)), "Failed import must not report success")
                assertFalse(hasText(screen.scene, labels.importedCount(2)), "Failed import must not report success")
                assertEquals(fixture.library, screen.persistedLibrary(), "A failed save must leave configs.json unchanged")
                assertEquals(fixture.library, screen.displayedLibrary(), "A failed save must not publish the computed library")
                assertEquals(0, screen.libraryTransformCount(), "The failed repository save must not complete its transform callback")
                assertEquals(1, readCount.get(), "Confirming an import must not reread the clipboard")
                assertEquals(0, writeCount.get(), "A failed import must not export clipboard text")

                val attempted = requireNotNull(screen.failedSaveCandidate()) {
                    "The repository should observe the computed library before the injected save failure"
                }
                val attemptedTarget = attempted.configs.single { it.id == fixture.target.id }
                val attemptedState = attemptedTarget.toTrafficConfigState(ResourceUserAgent)
                assertEquals(ProxyAppListModeGlobal, attemptedState.proxyAppListMode)
                assertEquals(
                    if (confirmAction == labels.merge) {
                        fixture.target.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps + importedApps
                    } else {
                        importedApps
                    },
                    attemptedState.proxyAppListSelectedApps,
                )
                assertTargetEnvelopeAndSections(
                    attemptedTarget,
                    fixture.target,
                    proxyServer = "target.example:443",
                    rule = "DOMAIN,target.example,DIRECT",
                    vendorValue = "target-vendor",
                )
                assertLibraryEnvelope(attempted, fixture.library)
            }
        }
    }

    @Test
    fun malformedUserIdShowsLocalizedErrorWithoutDialogOrLibraryMutation() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        val readCount = AtomicInteger()
        withScreen(
            fixture = fixture,
            labels = labels,
            width = 1100,
            height = 800,
            readClipboard = { readCount.incrementAndGet(); "mode=whitelist\nx:com.example.invalid" },
        ) { screen ->
            openPerApp(screen, labels)
            clickContentDescription(screen.scene, labels.moreActions)
            clickTextAction(screen.scene, labels.importAction)

            assertNotNull(awaitVisibleText(screen.scene, labels.invalidUserId, 1100, 800))
            assertFalse(hasText(screen.scene, labels.importTitle), "Invalid clipboard data should not open confirmation")
            assertEquals(fixture.library, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount())
            assertEquals(1, readCount.get())
        }
    }

    @Test
    fun exportUsesInjectedClipboardWithoutInventedDesktopAppInventoryOrWrites() = runBlocking {
        val labels = labels()
        val fixture = fixture()
        val writtenClipboard = AtomicReference<String?>(null)
        val readCount = AtomicInteger()
        withScreen(
            fixture = fixture,
            labels = labels,
            width = 1100,
            height = 800,
            readClipboard = { readCount.incrementAndGet(); error("Export must not read the clipboard") },
            writeClipboard = { text -> writtenClipboard.set(text) },
        ) { screen ->
            openPerApp(screen, labels)
            assertFalse(hasText(screen.scene, labels.searchApps), "Desktop must not show the Android package search field")
            assertFalse(hasText(screen.scene, "com.example.existing"), "Desktop must not invent installed-app rows")
            assertFalse(hasText(screen.scene, "legacy.unknown"), "Desktop must not show raw package-selection rows")
            assertFalse(hasEditableTextField(screen.scene), "Desktop Per-App must not expose a package-name text field")

            clickContentDescription(screen.scene, labels.moreActions)
            assertFalse(hasText(screen.scene, labels.showSystemApps), "Desktop More must omit Android's Show system apps action")
            assertNotNull(awaitVisibleClickableText(screen.scene, labels.importAction, 1100, 800))
            assertNotNull(awaitVisibleClickableText(screen.scene, labels.exportAction, 1100, 800))
            clickTextAction(screen.scene, labels.exportAction)

            assertTrue(
                awaitCondition(screen.scene, "clipboard export") {
                    writtenClipboard.get() == "mode=blacklist\nlegacy.unknown\n10:com.example.existing"
                },
                "Export should write the current mode and selected IDs through the injected callback",
            )
            assertEquals(0, readCount.get())
            assertEquals(fixture.library, screen.persistedLibrary())
            assertEquals(0, screen.libraryTransformCount(), "Export must not persist a profile change")
        }
    }

    @Test
    fun rendersPerAppBodyModeMenuAndImportDialogAtNarrowAndWideViewports() = runBlocking {
        val labels = labels()
        for ((width, height) in listOf(390 to 844, 1100 to 800)) {
            val fixture = fixture(
                targetSelectedApps = listOf("0:com.example.existing", "10:com.example.other"),
            )
            withScreen(
                fixture = fixture,
                labels = labels,
                width = width,
                height = height,
                readClipboard = {
                    "0:com.example.existing\n10:com.example.other"
                },
            ) { screen ->
                openPerApp(screen, labels)
                assertFullyVisible(awaitVisibleText(screen.scene, labels.perAppTitle, width, height), width, height, "Per-App title")
                assertFullyVisible(
                    awaitVisibleText(screen.scene, labels.capabilityNotice, width, height),
                    width,
                    height,
                    "Desktop capability notice",
                )
                assertFullyVisible(
                    awaitVisibleContentDescription(screen.scene, labels.modeAction, width, height),
                    width,
                    height,
                    "mode action",
                )
                assertFullyVisible(
                    awaitVisibleContentDescription(screen.scene, labels.moreActions, width, height),
                    width,
                    height,
                    "More action",
                )
                assertTopLeftBackVisible(screen.scene, width, height)
                assertFalse(hasText(screen.scene, labels.save))
                assertFalse(hasContentDescription(screen.scene, labels.save))
                saveSnapshot(screen.scene, "body", width, height)

                clickContentDescription(screen.scene, labels.modeAction)
                val whitelistOption = requireNotNull(awaitVisibleText(screen.scene, labels.whitelistMode, width, height)) {
                    "Whitelist mode option should fit inside ${width}x$height"
                }
                assertFullyVisible(whitelistOption, width, height, "whitelist mode option")
                val globalOption = requireNotNull(awaitVisibleText(screen.scene, labels.globalMode, width, height)) {
                    "All-app mode option should fit inside ${width}x$height"
                }
                assertFullyVisible(globalOption, width, height, "all-app mode option")
                saveSnapshot(screen.scene, "mode-menu", width, height)
                clickTextAction(screen.scene, labels.whitelistMode)
                assertEquals(
                    ProxyAppListModeWhitelist,
                    screen.persistedLibrary().configs.single { it.id == fixture.target.id }
                        .toTrafficConfigState(ResourceUserAgent).proxyAppListMode,
                )

                openImportDialog(screen.scene, labels)
                val dialogTitle = requireNotNull(awaitVisibleText(screen.scene, labels.importTitle, width, height)) {
                    "Import dialog title should fit inside ${width}x$height"
                }
                assertFullyVisible(dialogTitle, width, height, "import dialog title")
                val dialogMessage = requireNotNull(awaitVisibleText(screen.scene, labels.importCount(2), width, height)) {
                    "Detected-count message should fit inside ${width}x$height"
                }
                assertFullyVisible(dialogMessage, width, height, "import dialog message")
                assertFullyVisible(
                    requireNotNull(awaitVisibleClickableText(screen.scene, labels.merge, width, height)),
                    width,
                    height,
                    "Merge dialog action",
                )
                assertFullyVisible(
                    requireNotNull(awaitVisibleClickableText(screen.scene, labels.replace, width, height)),
                    width,
                    height,
                    "Replace dialog action",
                )
                saveSnapshot(screen.scene, "import-dialog", width, height)

                clickTextAction(screen.scene, labels.merge)
                assertTrue(awaitVisibleText(screen.scene, labels.importedCount(0), width, height) != null)
                val saved = screen.persistedLibrary().configs.single { it.id == fixture.target.id }
                    .toTrafficConfigState(ResourceUserAgent)
                assertEquals(ProxyAppListModeWhitelist, saved.proxyAppListMode)
                assertEquals(fixture.target.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps, saved.proxyAppListSelectedApps)
                assertPerAppDocument(
                    screen.persistedLibrary().configs.single { it.id == fixture.target.id },
                    expectedMode = "whitelist",
                    expectedSelectedApps = fixture.target.toTrafficConfigState(ResourceUserAgent).proxyAppListSelectedApps,
                )
            }
        }
    }

    private suspend fun openPerApp(screen: PerAppScreen, labels: Labels) {
        clickRowContentDescriptionAction(screen.scene, labels.targetName, "Ещё")
        clickTextAction(screen.scene, labels.uiEdit)
        assertNotNull(awaitEditableText(screen.scene, labels.targetName), "More → UI Edit should open Basics for target 7")
        val perAppSectionText = scrollBasicsToVisibleSection(screen, labels.perAppSection)
        val perAppSectionAction = requireNotNull(perAppSectionText.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        }) { "The fully visible Per-App section text should retain its public click action" }
        clickNodeAction(screen.scene, perAppSectionAction, labels.perAppSection)
        assertNotNull(awaitVisibleText(screen.scene, labels.perAppTitle, screen.width, screen.height))
        assertEquals(screen.fixture.sibling.id, screen.persistedLibrary().selectedConfigId)
        assertEquals(0, screen.libraryTransformCount(), "Navigating to Per-App should not write the library")
    }

    private suspend fun scrollBasicsToVisibleSection(
        screen: PerAppScreen,
        sectionLabel: String,
    ): SemanticsNode {
        val scene = screen.scene
        val initialSectionText = visibleClickableText(
            scene = scene,
            label = sectionLabel,
            width = screen.width,
            height = screen.height,
        )
        if (initialSectionText != null) return initialSectionText

        val initialScrollNode = requireNotNull(largestVerticalScroller(scene)) {
            "Basics should expose a public vertical ScrollBy action before reaching '$sectionLabel' " +
                "at ${screen.width}x${screen.height}"
        }
        val initialRange = initialScrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
        val initialPosition = initialRange.value()
        var lastPosition = initialPosition
        var maximumPosition = initialRange.maxValue()
        var steps = 0
        var sectionText: SemanticsNode? = null
        val maximumSteps = 12
        val deadline = System.nanoTime() + 3_000_000_000L
        val scrollDiagnostic = {
            "viewport=${screen.width}x${screen.height} before=$initialPosition after=$lastPosition " +
                "max=$maximumPosition steps=$steps/$maximumSteps"
        }

        assertTrue(
            maximumPosition > initialPosition + ScrollRangeEpsilon,
            "Basics should have remaining vertical range while '$sectionLabel' is outside the viewport; ${scrollDiagnostic()}",
        )

        while (sectionText == null && steps < maximumSteps && System.nanoTime() < deadline) {
            val scrollNode = requireNotNull(largestVerticalScroller(scene)) {
                "Basics should keep exposing a vertical scroller while reaching '$sectionLabel'; ${scrollDiagnostic()}"
            }
            val range = scrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
            val beforeStep = range.value()
            maximumPosition = range.maxValue()
            if (maximumPosition <= beforeStep + ScrollRangeEpsilon) break

            val requestedDelta = screen.height * 0.65f
            val scrollBy = requireNotNull(scrollNode.config[SemanticsActions.ScrollBy].action) {
                "Basics should expose its public ScrollBy action; ${scrollDiagnostic()}"
            }
            scrollBy(0f, requestedDelta)
            steps++

            val progressDeadline = minOf(deadline, System.nanoTime() + 300_000_000L)
            var afterStep = beforeStep
            do {
                render(scene)
                sectionText = visibleClickableText(
                    scene = scene,
                    label = sectionLabel,
                    width = screen.width,
                    height = screen.height,
                )
                val updatedRange = largestVerticalScroller(scene)
                    ?.config
                    ?.get(SemanticsProperties.VerticalScrollAxisRange)
                if (updatedRange != null) {
                    afterStep = updatedRange.value()
                    maximumPosition = updatedRange.maxValue()
                }
                if (afterStep > beforeStep + ScrollRangeEpsilon || sectionText != null) break
                delay(8L)
            } while (System.nanoTime() < progressDeadline)

            val stepDiagnostic =
                "viewport=${screen.width}x${screen.height} before=$initialPosition " +
                    "stepBefore=$beforeStep after=$afterStep max=$maximumPosition steps=$steps/$maximumSteps"
            assertTrue(
                afterStep > beforeStep + ScrollRangeEpsilon,
                "Basics ScrollBy should make bounded vertical progress toward '$sectionLabel'; $stepDiagnostic",
            )
            lastPosition = afterStep
        }

        assertTrue(
            lastPosition > initialPosition + ScrollRangeEpsilon,
            "Basics should advance before the offscreen '$sectionLabel' card is clicked; ${scrollDiagnostic()}",
        )
        return requireNotNull(sectionText) {
            "The '$sectionLabel' Basics card should become rendered, clickable, and fully visible within the bounded scroll; ${scrollDiagnostic()}"
        }
    }

    private fun visibleClickableText(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? {
        for (candidate in currentNodes(scene)) {
            if (candidate.hasText(label) && candidate.isFullyInside(width, height)) {
                if (candidate.ancestorsIncludingSelf().any { ancestor ->
                    ancestor.config.contains(SemanticsActions.OnClick)
                }) return candidate
            }
        }
        return null
    }

    private fun largestVerticalScroller(scene: ImageComposeScene): SemanticsNode? =
        currentNodes(scene)
            .filter { candidate ->
                candidate.config.contains(SemanticsActions.ScrollBy) &&
                    candidate.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            }
            .maxByOrNull { candidate -> candidate.boundsInRoot.width * candidate.boundsInRoot.height }

    private suspend fun openImportDialog(scene: ImageComposeScene, labels: Labels) {
        clickContentDescription(scene, labels.moreActions)
        clickTextAction(scene, labels.importAction)
        assertNotNull(awaitText(scene, labels.importTitle), "Valid clipboard data should open the import-mode dialog")
    }

    private suspend fun awaitPersistedProfile(
        screen: PerAppScreen,
        profileId: Int,
        predicate: (ConfigProfile) -> Boolean,
    ): ConfigProfile {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            render(screen.scene)
            val profile = screen.persistedLibrary().configs.singleOrNull { it.id == profileId }
            if (profile != null && predicate(profile)) return profile
            delay(8L)
        }
        error("Profile $profileId did not reach the expected persisted state")
    }

    private suspend fun awaitLibrary(
        screen: PerAppScreen,
        predicate: (DesktopConfigLibrary) -> Boolean,
    ): DesktopConfigLibrary {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            render(screen.scene)
            val library = screen.persistedLibrary()
            if (predicate(library)) return library
            delay(8L)
        }
        error("The temporary config library did not reach the expected persisted state")
    }

    private suspend fun awaitCondition(
        scene: ImageComposeScene,
        description: String,
        predicate: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            render(scene)
            if (predicate()) return true
            delay(8L)
        }
        return false
    }

    private suspend fun clickTextAction(scene: ImageComposeScene, label: String) {
        val node = requireNotNull(awaitClickableText(scene, label)) { "Expected rendered clickable text '$label'" }
        val target = requireNotNull(node.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        }) { "Expected '$label' to have a clickable ancestor" }
        clickNodeAction(scene, target, label)
    }

    private suspend fun clickContentDescription(scene: ImageComposeScene, label: String) {
        val node = requireNotNull(awaitContentDescription(scene, label)) { "Expected accessible control '$label'" }
        val target = node.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        } ?: node
        clickNodeAction(scene, target, label)
    }

    private suspend fun clickRowContentDescriptionAction(
        scene: ImageComposeScene,
        rowLabel: String,
        actionLabel: String,
    ) {
        val row = requireNotNull(awaitNode(scene) { candidate ->
            candidate.hasText(rowLabel) && candidate.ancestorsIncludingSelf().any { ancestor ->
                ancestor.config.contains(SemanticsActions.OnClick) &&
                    ancestor.descendants().any { child -> child.hasContentDescription(actionLabel) }
            }
        }) { "Expected profile row '$rowLabel' with the '$actionLabel' action" }
        val actionNode = requireNotNull(row.ancestorsIncludingSelf().flatMap { it.descendants() }
            .firstOrNull { candidate -> candidate.hasContentDescription(actionLabel) }) {
            "Expected '$actionLabel' inside profile row '$rowLabel'"
        }
        val target = actionNode.ancestorsIncludingSelf().firstOrNull { candidate ->
            candidate.config.contains(SemanticsActions.OnClick)
        } ?: actionNode
        clickNodeAction(scene, target, "$actionLabel in $rowLabel")
    }

    private fun clickNodeAction(scene: ImageComposeScene, node: SemanticsNode, description: String) {
        val onClick = requireNotNull(node.config[SemanticsActions.OnClick].action) {
            "$description should expose its public OnClick action"
        }
        assertTrue(onClick(), "$description OnClick should succeed")
        render(scene)
    }

    private suspend fun clickTopBarBack(scene: ImageComposeScene, width: Int, height: Int) {
        val back = requireNotNull(awaitNode(scene) { candidate ->
            val center = candidate.boundsInRoot.center
            candidate.config.contains(SemanticsActions.OnClick) &&
                candidate.boundsInRoot.width > 0f && candidate.boundsInRoot.height > 0f &&
                center.x in 0f..72f && center.y in 0f..80f
        }) { "Expected a public top-left Back action at ${width}x$height" }
        clickNodeAction(scene, back, "Top-bar Back")
    }

    private suspend fun awaitVisibleText(scene: ImageComposeScene, label: String, width: Int, height: Int): SemanticsNode? =
        awaitVisibleNode(scene, width, height) { candidate -> candidate.hasText(label) }

    private suspend fun awaitVisibleClickableText(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate ->
        candidate.hasText(label) && candidate.ancestorsIncludingSelf().any { ancestor ->
            ancestor.config.contains(SemanticsActions.OnClick)
        }
    }

    private suspend fun awaitVisibleContentDescription(
        scene: ImageComposeScene,
        label: String,
        width: Int,
        height: Int,
    ): SemanticsNode? = awaitVisibleNode(scene, width, height) { candidate -> candidate.hasContentDescription(label) }

    private suspend fun awaitClickableText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene) { candidate ->
            candidate.hasText(label) && candidate.ancestorsIncludingSelf().any { ancestor ->
                ancestor.config.contains(SemanticsActions.OnClick)
            }
        }

    private suspend fun awaitText(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene) { candidate -> candidate.hasText(label) }

    private suspend fun awaitContentDescription(scene: ImageComposeScene, label: String): SemanticsNode? =
        awaitNode(scene) { candidate -> candidate.hasContentDescription(label) }

    private suspend fun awaitEditableText(scene: ImageComposeScene, text: String): SemanticsNode? =
        awaitNode(scene) { candidate ->
            candidate.config.contains(SemanticsActions.SetText) &&
                candidate.config.contains(SemanticsProperties.EditableText) &&
                candidate.config[SemanticsProperties.EditableText].text == text
        }

    private suspend fun awaitVisibleNode(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? = awaitNode(scene) { candidate -> candidate.isFullyInside(width, height) && predicate(candidate) }

    private suspend fun awaitNode(
        scene: ImageComposeScene,
        predicate: (SemanticsNode) -> Boolean,
    ): SemanticsNode? {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            render(scene)
            currentNodes(scene).firstOrNull(predicate)?.let { return it }
            delay(8L)
        }
        return null
    }

    private fun hasText(scene: ImageComposeScene, label: String): Boolean =
        currentNodes(scene).any { candidate -> candidate.hasText(label) }

    private fun hasContentDescription(scene: ImageComposeScene, label: String): Boolean =
        currentNodes(scene).any { candidate -> candidate.hasContentDescription(label) }

    private fun hasEditableTextField(scene: ImageComposeScene): Boolean =
        currentNodes(scene).any { candidate ->
            candidate.config.contains(SemanticsActions.SetText) &&
                candidate.config.contains(SemanticsProperties.EditableText)
        }

    private fun assertTopLeftBackVisible(scene: ImageComposeScene, width: Int, height: Int) {
        assertTrue(
            currentNodes(scene).any { candidate ->
                val center = candidate.boundsInRoot.center
                candidate.config.contains(SemanticsActions.OnClick) &&
                    candidate.isFullyInside(width, height) &&
                    center.x in 0f..72f && center.y in 0f..80f
            },
            "A fully visible Back action should remain at the top left of ${width}x$height",
        )
    }

    private fun assertFullyVisible(node: SemanticsNode?, width: Int, height: Int, description: String) {
        val visibleNode = assertNotNull(node, "$description should be present in the ${width}x$height viewport")
        assertTrue(
            visibleNode.isFullyInside(width, height),
            "$description bounds ${visibleNode.boundsInRoot} should fit inside ${width}x$height",
        )
    }

    private fun saveSnapshot(scene: ImageComposeScene, name: String, width: Int, height: Int) {
        settleScene(scene, name)
        val image = scene.render(advanceFrameTime())
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
                assertTrue(sampledColors.size >= 3, "$name should include visible controls and surfaces")
            } finally {
                pixels.close()
            }
            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                val outputDirectory = File(
                    System.getProperty("user.dir"),
                    "build/reports/desktop-traffic-config-per-app-screen",
                )
                assertTrue(outputDirectory.mkdirs() || outputDirectory.isDirectory)
                val output = File(outputDirectory, "$name-${width}x$height.png")
                output.writeBytes(encoded.bytes)
                assertTrue(output.length() > 1_024L, "$name PNG should contain rendered UI")
            } finally {
                encoded.close()
            }
        } finally {
            image.close()
        }
    }

    private fun captureBackNavigationDiagnostics(
        scene: ImageComposeScene,
        width: Int,
        height: Int,
    ): String {
        val nodes = currentNodes(scene).toList()
        fun SemanticsNode.intersectsViewport(): Boolean {
            val bounds = boundsInRoot
            return bounds.width > 0f && bounds.height > 0f &&
                bounds.right > 0f && bounds.left < width.toFloat() &&
                bounds.bottom > 0f && bounds.top < height.toFloat()
        }

        val visibleText = nodes.flatMap { node ->
            if (!node.intersectsViewport()) emptyList()
            else node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                .map { text -> "${text.text} @ ${node.boundsInRoot}" }
        }.distinct()
        val editableFields = nodes.filter { node ->
            node.config.contains(SemanticsActions.SetText) &&
                node.config.contains(SemanticsProperties.EditableText)
        }.map { node ->
            val value = node.config[SemanticsProperties.EditableText].text
            "${value.take(160)} @ ${node.boundsInRoot} intersects=${node.intersectsViewport()}"
        }
        val verticalScrollers = nodes.filter { node ->
            node.config.contains(SemanticsActions.ScrollBy) &&
                node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
        }.map { node ->
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            "value=${range.value()} max=${range.maxValue()} bounds=${node.boundsInRoot}"
        }
        val summary = buildString {
            appendLine("Per-App Back navigation diagnostics at ${width}x$height")
            appendLine("Visible/intersecting text (${visibleText.size}):")
            visibleText.forEach { appendLine("  $it") }
            appendLine("Editable fields (${editableFields.size}):")
            editableFields.forEach { appendLine("  $it") }
            appendLine("Vertical scrollers (${verticalScrollers.size}):")
            verticalScrollers.forEach { appendLine("  $it") }
        }
        val outputDirectory = File(
            System.getProperty("user.dir"),
            "build/reports/desktop-traffic-config-per-app-screen",
        )
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory)
        val outputName = "back-navigation-diagnostic"
        val summaryFile = File(outputDirectory, "$outputName.txt")
        summaryFile.writeText(summary)
        saveSnapshot(scene, outputName, width, height)
        return "snapshot=${File(outputDirectory, "$outputName-${width}x$height.png")}; " +
            "summary=$summaryFile\n$summary"
    }

    private fun settleScene(scene: ImageComposeScene, description: String) {
        val minimumFrames = 30
        val requiredStableFrames = 3
        val maximumFrames = 90
        var previousPixels: IntArray? = null
        var firstBuffer: IntArray? = null
        var secondBuffer: IntArray? = null
        var stableFrames = 0
        repeat(maximumFrames) { frameIndex ->
            val image = scene.render(advanceFrameTime())
            try {
                val pixelMap = requireNotNull(image.peekPixels())
                try {
                    if (firstBuffer == null) {
                        firstBuffer = IntArray(image.width * image.height)
                        secondBuffer = IntArray(image.width * image.height)
                    }
                    val first = requireNotNull(firstBuffer)
                    val second = requireNotNull(secondBuffer)
                    val currentPixels = if (previousPixels === first) second else first
                    var index = 0
                    for (y in 0 until image.height) {
                        for (x in 0 until image.width) currentPixels[index++] = pixelMap.getColor(x, y)
                    }
                    if (previousPixels != null && currentPixels.contentEquals(previousPixels)) {
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
            if (frameIndex + 1 >= minimumFrames && stableFrames >= requiredStableFrames) return
        }
        error("The $description view did not reach stable rendered frames")
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

    private fun SemanticsNode.hasText(label: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { text -> text.text.equals(label, ignoreCase = true) }

    private fun SemanticsNode.hasContentDescription(label: String): Boolean =
        config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .any { value -> value.equals(label, ignoreCase = true) }

    private fun SemanticsNode.isFullyInside(width: Int, height: Int): Boolean {
        val bounds = boundsInRoot
        return bounds.width > 0f && bounds.height > 0f &&
            bounds.left >= 0f && bounds.top >= 0f &&
            bounds.right <= width.toFloat() && bounds.bottom <= height.toFloat()
    }

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

    private suspend fun labels(): Labels = Labels(
        uiEdit = getString(Res.string.configs_ui_edit),
        targetName = "Per-App target",
        siblingName = "Selected sibling",
        perAppSection = getString(Res.string.configs_per_app),
        perAppTitle = getString(Res.string.proxy_app_list_title),
        capabilityNotice = getString(Res.string.configs_per_app_desktop_notice),
        modeAction = getString(Res.string.proxy_app_list_mode),
        moreActions = getString(Res.string.proxy_app_list_more_actions),
        whitelistMode = getString(Res.string.proxy_app_list_mode_whitelist),
        globalMode = getString(Res.string.proxy_app_list_mode_global),
        importAction = getString(Res.string.common_import_from_clipboard),
        exportAction = getString(Res.string.common_export_to_clipboard),
        showSystemApps = getString(Res.string.proxy_app_list_show_system_apps),
        searchApps = getString(Res.string.proxy_app_list_search_label),
        importTitle = getString(Res.string.proxy_app_list_import_clipboard_title),
        importMessageTemplate = getString(Res.string.proxy_app_list_import_clipboard_message),
        importedTemplate = getString(Res.string.proxy_app_list_imported),
        invalidUserId = getString(Res.string.proxy_app_list_import_invalid_user),
        merge = getString(Res.string.common_merge_import),
        replace = getString(Res.string.common_replace_existing),
        save = getString(Res.string.common_save),
    )

    private fun fixture(
        targetSelectedApps: List<String> = listOf("legacy.unknown", "10:com.example.existing"),
    ): Fixture {
        val target = profile(
            id = TargetProfileId,
            name = "Per-App target",
            sourceUrl = "https://target.example/per-app.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 456L,
            mode = ProxyAppListModeBlacklist,
            selectedApps = targetSelectedApps,
            proxyServer = "target.example:443",
            rule = "DOMAIN,target.example,DIRECT",
            vendorValue = "target-vendor",
        )
        val sibling = profile(
            id = SelectedProfileId,
            name = "Selected sibling",
            sourceUrl = "https://sibling.example/profile.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 111L,
            selectedApps = listOf("legacy.sibling"),
            proxyServer = "sibling.example:443",
            rule = "DOMAIN,sibling.example,PROXY",
            vendorValue = "sibling-vendor",
        )
        return Fixture(
            library = DesktopConfigLibrary(
                selectedConfigId = sibling.id,
                configs = listOf(target, sibling),
                nextConfigId = InitialNextConfigId,
            ),
            target = target,
            sibling = sibling,
        )
    }

    private fun profile(
        id: Int,
        name: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
        proxyServer: String,
        rule: String,
        vendorValue: String,
        mode: Int = ProxyAppListModeWhitelist,
        selectedApps: List<String> = emptyList(),
    ): ConfigProfile {
        val state = TrafficConfigState(
            id = id,
            name = name,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
            proxyAppListMode = mode,
            proxyAppListSelectedApps = selectedApps,
            rawConfig = """[General]
loglevel = warning

[Proxy]
server = $proxyServer

[Rule]
$rule

[Vendor]
vendor-option = $vendorValue
""",
        ).withSkipiSettingsInRawConfig()
        return ConfigProfile(
            id = id,
            name = state.name,
            content = state.rawConfig,
            sourceUrl = state.sourceUrl,
            updateLocked = state.updateLocked,
            lastUpdatedAtMillis = state.lastUpdatedAtMillis,
        )
    }

    private fun assertTargetEnvelopeAndSections(
        actual: ConfigProfile,
        expected: ConfigProfile,
        proxyServer: String,
        rule: String,
        vendorValue: String,
    ) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.sourceUrl, actual.sourceUrl)
        assertEquals(expected.updateLocked, actual.updateLocked)
        assertEquals(expected.lastUpdatedAtMillis, actual.lastUpdatedAtMillis)
        assertTrue(actual.content.contains("[Proxy]"))
        assertTrue(actual.content.contains("server = $proxyServer"))
        assertTrue(actual.content.contains("[Rule]\n$rule"))
        assertTrue(actual.content.contains("[Vendor]"))
        assertTrue(actual.content.contains("vendor-option = $vendorValue"))
    }

    private fun assertPerAppDocument(profile: ConfigProfile, expectedMode: String, expectedSelectedApps: List<String>) {
        val values = profile.content.skipiSectionValues()
        assertEquals(listOf(expectedMode), values["per-app-mode"])
        assertEquals(expectedSelectedApps, values["per-app-package"])
    }

    private fun assertLibraryEnvelope(actual: DesktopConfigLibrary, expected: DesktopConfigLibrary) {
        assertEquals(expected.selectedConfigId, actual.selectedConfigId)
        assertEquals(expected.nextConfigId, actual.nextConfigId)
        assertEquals(expected.configs.map(ConfigProfile::id), actual.configs.map(ConfigProfile::id))
        assertEquals(expected.configs.single { it.id == SelectedProfileId }, actual.configs.single { it.id == SelectedProfileId })
    }

    private fun Labels.importCount(count: Int): String = importMessageTemplate.replace("{count}", count.toString())
    private fun Labels.importedCount(count: Int): String = importedTemplate.replace("{count}", count.toString())

    private data class Labels(
        val uiEdit: String,
        val targetName: String,
        val siblingName: String,
        val perAppSection: String,
        val perAppTitle: String,
        val capabilityNotice: String,
        val modeAction: String,
        val moreActions: String,
        val whitelistMode: String,
        val globalMode: String,
        val importAction: String,
        val exportAction: String,
        val showSystemApps: String,
        val searchApps: String,
        val importTitle: String,
        val importMessageTemplate: String,
        val importedTemplate: String,
        val invalidUserId: String,
        val merge: String,
        val replace: String,
        val save: String,
    )

    private data class Fixture(
        val library: DesktopConfigLibrary,
        val target: ConfigProfile,
        val sibling: ConfigProfile,
    )

    private suspend fun withScreen(
        fixture: Fixture,
        labels: Labels,
        width: Int,
        height: Int,
        readClipboard: suspend () -> String? = { error("Unexpected clipboard read") },
        writeClipboard: (String) -> Unit = { error("Unexpected clipboard write") },
        block: suspend (PerAppScreen) -> Unit,
    ) {
        val root = Files.createTempDirectory("desktop-per-app-screen-").toFile()
        val screen = PerAppScreen(
            root = root,
            fixture = fixture,
            width = width,
            height = height,
            readClipboard = readClipboard,
            writeClipboard = writeClipboard,
        )
        try {
            block(screen)
        } finally {
            screen.close()
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private class PerAppScreen(
        root: File,
        val fixture: Fixture,
        val width: Int,
        val height: Int,
        readClipboard: suspend () -> String?,
        writeClipboard: (String) -> Unit,
    ) : AutoCloseable {
        private val configPath: Path = root.toPath().resolve("configs.json")
        private val resourceFiles = DesktopProfileResourceFiles(File(root, "resources"))
        private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val transformCount = AtomicInteger()
        private val resourceUserAgent = ResourceUserAgent
        private val resourceServices: DesktopResourceFileServices
        private val repository: DesktopTrafficConfigRepository
        private val nextRepositorySaveFailure = AtomicReference<Exception?>(null)
        private val failedSaveCandidate = AtomicReference<DesktopConfigLibrary?>(null)
        private var frameTimeNanos = 1_000_000_000L
        var latestLibrary by mutableStateOf(fixture.library)
            private set
        val scene = ImageComposeScene(width = width, height = height)

        init {
            DesktopConfigLibraries.save(configPath, fixture.library).getOrThrow()
            latestLibrary = loadPersistedLibrary()
            repository = DesktopTrafficConfigRepository(
                initialLibrary = latestLibrary,
                readLibrary = ::loadPersistedLibrary,
                saveLibrary = { candidate ->
                    val failure = nextRepositorySaveFailure.getAndSet(null)
                    if (failure == null) {
                        DesktopConfigLibraries.save(configPath, candidate)
                    } else {
                        failedSaveCandidate.set(candidate)
                        Result.failure(failure)
                    }
                },
                publishLibrary = { library -> latestLibrary = library },
            )
            resourceServices = DesktopResourceFileServices(
                scope = hostScope,
                resourceFiles = resourceFiles,
                readSettings = ::DesktopAppSettings,
                readProfile = { profileId ->
                    latestLibrary.configs.singleOrNull { profile -> profile.id == profileId }
                        ?.toTrafficConfigState(resourceUserAgent)
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
                        onTransformConfigLibrary = { transform ->
                            repository.transformLatest(transform).also { updated ->
                                transformCount.incrementAndGet()
                                latestLibrary = updated
                            }
                        },
                        fetchUserAgent = resourceUserAgent,
                        fetchTimeoutSeconds = 5,
                        fetchProfileDocument = { _, _ -> error("Unexpected profile fetch in Per-App test") },
                        contentPadding = PaddingValues(),
                        resourceActionScope = hostScope,
                        onResourceFileStatusMessage = {},
                        desktopSettings = DesktopAppSettings(confirmDeletion = true),
                        exportProxyServerFullJson = { error("Unexpected proxy-server export in Per-App test") },
                        onApplyGlobalProxyGroupResult = { _, _ -> error("Unexpected global group apply in Per-App test") },
                        copyTextToClipboard = writeClipboard,
                        readPerAppClipboardText = readClipboard,
                        proxyServers = emptyList(),
                        subscriptionCatalog = SubscriptionCatalog(),
                        resourceFiles = resourceFiles,
                        latencyByServerId = emptyMap(),
                        resourceServices = resourceServices,
                        resourceFileStatusMessage = "",
                    )
                }
            }
            render()
        }

        fun persistedLibrary(): DesktopConfigLibrary = loadPersistedLibrary()

        fun displayedLibrary(): DesktopConfigLibrary = latestLibrary

        fun libraryTransformCount(): Int = transformCount.get()

        fun failNextRepositorySave(failure: Exception) {
            failedSaveCandidate.set(null)
            nextRepositorySaveFailure.set(failure)
        }

        fun failedSaveCandidate(): DesktopConfigLibrary? = failedSaveCandidate.get()

        fun persistExternal(library: DesktopConfigLibrary) {
            val saved = repository.transformLatest { library }
            latestLibrary = saved
            render()
        }

        fun removeTargetExternallyWithoutPublishing(library: DesktopConfigLibrary) {
            DesktopConfigLibraries.save(configPath, library).getOrThrow()
            render()
        }

        private fun loadPersistedLibrary(): DesktopConfigLibrary =
            DesktopConfigLibraries.load(configPath).getOrThrow()

        private fun render() {
            scene.render(advanceFrameTime()).close()
        }

        private fun advanceFrameTime(): Long {
            frameTimeNanos += 16_666_667L
            return frameTimeNanos
        }

        override fun close() {
            scene.close()
            hostScope.cancel()
        }
    }

    private companion object {
        const val TargetProfileId = 7
        const val SelectedProfileId = 3
        const val LateSiblingId = 15
        const val InitialNextConfigId = 29
        const val LateNextConfigId = 40
        const val ScrollRangeEpsilon = 0.000001f
        const val ResourceUserAgent = "Skipi/PerApp/DesktopTest"
    }
}
