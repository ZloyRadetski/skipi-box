package app.skipi.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Right mouse button as the desktop counterpart of Android's long press: holding the button down is not
 * discoverable with a mouse, a context click is. Put it before (outside) the element's own click handling so the
 * press is consumed before a tap or long press can start.
 */
fun Modifier.onSecondaryClick(enabled: Boolean = true, onClick: (Offset) -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        onClick(event.changes.first().position)
                    }
                }
            }
        }
    }
