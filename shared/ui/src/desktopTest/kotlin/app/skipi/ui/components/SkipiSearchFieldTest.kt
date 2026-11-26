// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.skipi.ui.settings.SkipiSettingsHomeScreen
import app.skipi.ui.settings.SkipiSettingsHomeState
import app.skipi.ui.settings.search.LocalSettingsSearchController
import app.skipi.ui.settings.search.SettingsSearchController
import app.skipi.ui.theme.LocalAppColors
import app.skipi.ui.theme.ProvideSkipiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SkipiSearchFieldTest {
    @Test
    fun theFieldIsLowAndAsWideAsItsPlace() = runComposeUiTest {
        setContent {
            ProvideSkipiTheme(colors = LocalAppColors.current) {
                Box(Modifier.size(360.dp, 200.dp)) { SkipiSearchField(value = "", onValueChange = {}, placeholder = "Find") }
            }
        }
        onNode(hasSetTextAction()).assertHeightIsEqualTo(44.dp).assertWidthIsEqualTo(360.dp)
    }

    @Test
    fun typedTextCanBeClearedWithoutTheFieldGrowing() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            ProvideSkipiTheme(colors = LocalAppColors.current) {
                Box(Modifier.size(360.dp, 200.dp)) {
                    SkipiSearchField(value = query, onValueChange = { query = it }, placeholder = "Find")
                }
            }
        }
        onNodeWithTag("search-clear").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextInput("proxy")
        waitForIdle()
        assertEquals("proxy", query)
        onNode(hasSetTextAction()).assertHeightIsEqualTo(44.dp)
        onNodeWithTag("search-clear", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals("", query)
    }

    @Test
    fun theSettingsSearchUsesTheSameField() = runComposeUiTest {
        setContent {
            ProvideSkipiTheme(colors = LocalAppColors.current) {
                CompositionLocalProvider(LocalSettingsSearchController provides SettingsSearchController()) {
                    Box(Modifier.size(400.dp, 420.dp)) {
                        SkipiSettingsHomeScreen(
                            state = SkipiSettingsHomeState("v1", "1500", "1080", null),
                            padding = PaddingValues(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        onNode(hasSetTextAction()).assertHeightIsEqualTo(44.dp)
    }
}
