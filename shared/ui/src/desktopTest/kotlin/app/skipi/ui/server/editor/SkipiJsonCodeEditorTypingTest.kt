package app.skipi.ui.server.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SkipiJsonCodeEditorTypingTest {
    @Test
    fun typedTextReachesTheCallerAndStaysInTheField() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent {
            SkipiJsonCodeEditor(label = "JSON", value = text, onValueChange = { text = it })
        }
        onNode(hasSetTextAction()).performTextInput("{\"a\":1}")
        waitForIdle()
        assertEquals("{\"a\":1}", text)
    }
}

@OptIn(ExperimentalTestApi::class)
class SkipiJsonCodeEditorPlainHolderTypingTest {
    private class Holder { var json: String = "" }

    @Test
    fun typingWorksWhenTheCallerStoresIntoAPlainObjectThatIsNotObservable() = runComposeUiTest {
        val holder = Holder()
        setContent {
            SkipiJsonCodeEditor(label = "JSON", value = holder.json, onValueChange = { holder.json = it })
        }
        onNode(hasSetTextAction()).performTextInput("{\"a\":1}")
        waitForIdle()
        assertEquals("{\"a\":1}", holder.json)
        onNode(hasSetTextAction()).performTextInput(" ")
        waitForIdle()
        assertEquals("{\"a\":1} ", holder.json)
    }
}
