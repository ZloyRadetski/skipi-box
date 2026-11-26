// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.skipi.ui.settings.GeneralSettingsState
import app.skipi.ui.settings.SkipiGeneralSettingsScreen
import app.skipi.ui.settings.SkipiSettingsHomeScreen
import app.skipi.ui.settings.SkipiSettingsHomeState
import app.skipi.ui.theme.LocalAppColors
import app.skipi.ui.theme.ProvideSkipiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsSearchUiTest {
    private val controller = SettingsSearchController()
    private val entries = settingsSearchEntries(SettingsSearchHost())
    private val opened = mutableListOf<SettingsSearchPage>()
    private var keyboardHides = 0
    private val keyboard = object : SoftwareKeyboardController {
        override fun show() = Unit
        override fun hide() {
            keyboardHides++
        }
    }

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ProvideSkipiTheme(colors = LocalAppColors.current) {
            CompositionLocalProvider(
                LocalSettingsSearchController provides controller,
                LocalSoftwareKeyboardController provides keyboard,
            ) {
                Box(Modifier.size(400.dp, 420.dp)) { content() }
            }
        }
    }

    private fun ComposeUiTest.showHome() = show {
        SkipiSettingsHomeScreen(
            state = SkipiSettingsHomeState(versionLabel = "v1", tunMtu = "1500", localProxyPort = "1080", coreLogLevel = null),
            padding = PaddingValues(),
            onNavigate = {},
            onOpenSearchResult = { opened += it },
        )
    }

    private fun ComposeUiTest.showGeneral(trafficNotification: Boolean = false) = show {
        SkipiGeneralSettingsScreen(
            state = GeneralSettingsState(
                languageMode = 0,
                enableHaptics = true,
                enableTrafficStatsNotification = trafficNotification,
                trafficStatsNotificationRefreshIntervalSeconds = 1,
                enableResourceFileNotifications = false,
                enableDeletionConfirmation = false,
                enableBroadcastControl = false,
            ),
            padding = PaddingValues(),
            isWideScreen = false,
            onSettingsChange = {},
            onBack = {},
            onOpenUrlSchemes = {},
            onOpenBackupReset = {},
        )
    }

    private fun open(id: String) = controller.open(entries.first { it.id == id }, entries)

    /** Lets the page scroll to the row, without waiting for the highlight to end. */
    private fun ComposeUiTest.settle() = repeat(60) { mainClock.advanceTimeByFrame() }

    @Test
    fun theOverviewStartsWithTheSearchFieldAndNoTitleBar() = runComposeUiTest {
        showHome()
        waitForIdle()
        // The version was the title bar's subtitle; the About entry at the bottom still names it.
        onNodeWithText("v1").assertDoesNotExist()
        val field = onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        assertTrue(field.top < 32.dp, "The field starts at ${field.top}")
    }

    @Test
    fun typingShowsResultsAndAClickOpensTheRow() = runComposeUiTest {
        showHome()
        onNode(hasSetTextAction()).performTextInput("vibration")
        waitForIdle()
        onNodeWithTag("settings-search-result:settings_enable_haptics").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(listOf(SettingsSearchPage.General), opened)
        assertEquals(listOf("settings_enable_haptics"), controller.focus?.chain)
        // The query is kept for the way back.
        assertEquals("vibration", controller.query)
    }

    @Test
    fun openingAResultLeavesTheSearchFieldSoTheKeyboardCloses() = runComposeUiTest {
        showHome()
        onNode(hasSetTextAction()).performClick().performTextInput("vibration")
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
        val hidesWhileTyping = keyboardHides
        // A touch does not move the focus the way a mouse click does, so the click is sent as the action itself.
        onNodeWithTag("settings-search-result:settings_enable_haptics")
            .onChildren().filterToOne(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertTrue(keyboardHides > hidesWhileTyping, "The keyboard was left open")
        onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun resultsReplaceTheSectionsAndNothingFoundSaysSo() = runComposeUiTest {
        showHome()
        onNodeWithTag("settings-home-section:appearance").assertIsDisplayed()
        runOnIdle { controller.query = "qqqqzzzz" }
        waitForIdle()
        onNodeWithTag("settings-search-empty").assertIsDisplayed()
        onNodeWithTag("settings-home-section:appearance").assertDoesNotExist()
        runOnIdle { controller.query = "" }
        waitForIdle()
        onNodeWithTag("settings-home-section:appearance").assertIsDisplayed()
    }

    @Test
    fun openingAPageResultHighlightsNothing() = runComposeUiTest {
        open("page:SpeedTest")
        assertNull(controller.focus)
    }

    @Test
    fun aHiddenRowCarriesItsParentsAsFallback() = runComposeUiTest {
        open("settings_traffic_stats_notification_refresh_interval")
        assertEquals(
            listOf("settings_traffic_stats_notification_refresh_interval", "settings_traffic_stats_notification"),
            controller.focus?.chain,
        )
    }

    @Test
    fun thePageScrollsToTheRowHighlightsItAndForgetsIt() = runComposeUiTest {
        mainClock.autoAdvance = false
        open("settings_broadcast_control")
        val focus = controller.focus!!
        showGeneral()
        settle()
        assertEquals("settings_broadcast_control", focus.highlighted)
        onNodeWithTag("settings-anchor:settings_broadcast_control").assertIsDisplayed()
        mainClock.advanceTimeBy(5_000)
        assertNull(controller.focus)
    }

    @Test
    fun theFoundRowIsShownWhole() = runComposeUiTest {
        mainClock.autoAdvance = false
        open("settings_broadcast_control")
        showGeneral()
        settle()
        val row = onNodeWithTag("settings-anchor:settings_broadcast_control").getUnclippedBoundsInRoot()
        assertTrue(row.bottom <= 420.dp, "The row ends at ${row.bottom}")
        assertTrue(row.top >= 56.dp, "The row starts at ${row.top}, under the top bar")
    }

    @Test
    fun aPageComesBeforeARowThatMatchesAsWell() = runComposeUiTest {
        showHome()
        // Only the keywords of a VPN row and of the network automation page have this word.
        runOnIdle { controller.query = "wifi" }
        waitForIdle()
        val page = onNodeWithTag("settings-search-result:page:NetworkAutomation").getUnclippedBoundsInRoot()
        val row = onNodeWithTag("settings-search-result:settings_seamless_network_switching").getUnclippedBoundsInRoot()
        assertTrue(page.top < row.top)
    }

    @Test
    fun aRowSwitchedOffHighlightsTheSettingThatShowsIt() = runComposeUiTest {
        mainClock.autoAdvance = false
        open("settings_traffic_stats_notification_refresh_interval")
        val focus = controller.focus!!
        showGeneral(trafficNotification = false)
        settle()
        assertEquals("settings_traffic_stats_notification", focus.highlighted)
        onNodeWithTag("settings-anchor:settings_traffic_stats_notification").assertIsDisplayed()
    }

    @Test
    fun aShownChildRowIsHighlightedItself() = runComposeUiTest {
        mainClock.autoAdvance = false
        open("settings_traffic_stats_notification_refresh_interval")
        val focus = controller.focus!!
        showGeneral(trafficNotification = true)
        settle()
        assertEquals("settings_traffic_stats_notification_refresh_interval", focus.highlighted)
    }

    @Test
    fun anotherPageLeavesTheRequestAlone() = runComposeUiTest {
        mainClock.autoAdvance = false
        open("settings_kill_switch")
        showGeneral()
        settle()
        assertNull(controller.focus?.highlighted)
        assertEquals(listOf("settings_kill_switch"), controller.focus?.chain)
    }
}
