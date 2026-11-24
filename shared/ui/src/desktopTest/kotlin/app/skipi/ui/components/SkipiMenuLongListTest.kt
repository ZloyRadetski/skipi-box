package app.skipi.ui.components

import androidx.compose.foundation.layout.Box
import app.skipi.ui.theme.LocalAppColors
import app.skipi.ui.theme.ProvideSkipiTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkipiMenuLongListTest {
    private fun section(count: Int) = SkipiMenuSection((1..count).map { SkipiMenuItem(title = "Server $it") })

    @Test
    fun aLevelOverTheLimitIsLong() {
        assertFalse(listOf(section(LazyMenuRowThreshold)).hasLongLevel())
        assertTrue(listOf(section(LazyMenuRowThreshold + 1)).hasLongLevel())
        assertTrue(listOf(section(LazyMenuRowThreshold), section(1)).hasLongLevel())
    }

    @Test
    fun aLongSubmenuMakesTheWholeMenuLong() {
        val door = SkipiMenuItem(title = "Move", children = section(LazyMenuRowThreshold + 1).items)
        assertTrue(listOf(SkipiMenuSection(listOf(door))).hasLongLevel())
        assertEquals(false, listOf(SkipiMenuSection(listOf(SkipiMenuItem(title = "Plain")))).hasLongLevel())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aMenuOfThousandsOfRowsComposesOnlyTheVisibleOnes() = runComposeUiTest {
        setContent {
            ProvideSkipiTheme(colors = LocalAppColors.current) {
                Box { SkipiMenu(expanded = true, onDismissRequest = {}, sections = listOf(section(5000))) }
            }
        }
        waitForIdle()
        onNodeWithText("Server 1").assertIsDisplayed()
        assertEquals(0, onAllNodesWithTextCount("Server 5000"))
    }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
