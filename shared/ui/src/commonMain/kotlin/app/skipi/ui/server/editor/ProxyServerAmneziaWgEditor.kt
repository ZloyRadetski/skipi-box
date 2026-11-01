// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.*
import features.proxy.server.model.AmneziaWg
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import kotlin.reflect.KMutableProperty1

/** A field label: a localized string resource, or a literal for the protocol's own technical names. */
internal sealed interface AmneziaWgFieldLabel {
    data class Resource(val resource: StringResource) : AmneziaWgFieldLabel
    data class Literal(val text: String) : AmneziaWgFieldLabel
}

/** One text field of the AmneziaWG editor; [key] is also its stable list item key. */
internal class AmneziaWgEditorField(
    val key: String,
    val label: AmneziaWgFieldLabel,
    private val property: KMutableProperty1<AmneziaWg, String>,
    val digitsOnly: Boolean = false,
) {
    fun read(server: AmneziaWg): String = property.get(server)
    fun write(server: AmneziaWg, value: String) = property.set(server, value)
}

/** Connection fields, in the order the editor draws them. */
internal val amneziaWgPropertyFields: List<AmneziaWgEditorField> = listOf(
    AmneziaWgEditorField("remarks", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_remarks), AmneziaWg::remarks),
    AmneziaWgEditorField("server", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_server), AmneziaWg::server),
    AmneziaWgEditorField("port", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_port), AmneziaWg::port, digitsOnly = true),
    AmneziaWgEditorField("secretKey", AmneziaWgFieldLabel.Literal("SecretKey"), AmneziaWg::secretKey),
    AmneziaWgEditorField("publicKey", AmneziaWgFieldLabel.Literal("PublicKey"), AmneziaWg::publicKey),
    AmneziaWgEditorField("preSharedKey", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_preshared_key_optional), AmneziaWg::preSharedKey),
    AmneziaWgEditorField("reserved", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_reserved_optional), AmneziaWg::reserved),
    AmneziaWgEditorField("address", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_local_address_optional), AmneziaWg::address),
    AmneziaWgEditorField("mtu", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_mtu_optional), AmneziaWg::mtu, digitsOnly = true),
)

/** Obfuscation, AmneziaWG 3.0 and 3.1 fields, in the order the editor draws them. */
internal val amneziaWgObfuscationFields: List<AmneziaWgEditorField> = listOf(
    AmneziaWgEditorField("jc", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_jc), AmneziaWg::jc, digitsOnly = true),
    AmneziaWgEditorField("jmin", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_jmin), AmneziaWg::jmin, digitsOnly = true),
    AmneziaWgEditorField("jmax", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_jmax), AmneziaWg::jmax, digitsOnly = true),
    AmneziaWgEditorField("s1", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_s1), AmneziaWg::s1, digitsOnly = true),
    AmneziaWgEditorField("s2", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_s2), AmneziaWg::s2, digitsOnly = true),
    AmneziaWgEditorField("s3", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_s3), AmneziaWg::s3, digitsOnly = true),
    AmneziaWgEditorField("s4", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_s4), AmneziaWg::s4, digitsOnly = true),
    AmneziaWgEditorField("h1", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_h1), AmneziaWg::h1),
    AmneziaWgEditorField("h2", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_h2), AmneziaWg::h2),
    AmneziaWgEditorField("h3", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_h3), AmneziaWg::h3),
    AmneziaWgEditorField("h4", AmneziaWgFieldLabel.Resource(Res.string.proxy_editor_awg_h4), AmneziaWg::h4),
    AmneziaWgEditorField("i1", AmneziaWgFieldLabel.Literal("I1"), AmneziaWg::i1),
    AmneziaWgEditorField("i2", AmneziaWgFieldLabel.Literal("I2"), AmneziaWg::i2),
    AmneziaWgEditorField("i3", AmneziaWgFieldLabel.Literal("I3"), AmneziaWg::i3),
    AmneziaWgEditorField("i4", AmneziaWgFieldLabel.Literal("I4"), AmneziaWg::i4),
    AmneziaWgEditorField("i5", AmneziaWgFieldLabel.Literal("I5"), AmneziaWg::i5),
    AmneziaWgEditorField("headerProtectionKey", AmneziaWgFieldLabel.Literal("HeaderProtectionKey"), AmneziaWg::headerProtectionKey),
    AmneziaWgEditorField("contentPaddingAddition", AmneziaWgFieldLabel.Literal("ContentPaddingAddition"), AmneziaWg::contentPaddingAddition),
    AmneziaWgEditorField("rekeyAfterTime", AmneziaWgFieldLabel.Literal("RekeyAfterTime"), AmneziaWg::rekeyAfterTime),
    AmneziaWgEditorField("rekeyTimeout", AmneziaWgFieldLabel.Literal("RekeyTimeout"), AmneziaWg::rekeyTimeout),
    AmneziaWgEditorField("rejectAfterTime", AmneziaWgFieldLabel.Literal("RejectAfterTime"), AmneziaWg::rejectAfterTime),
    AmneziaWgEditorField("keepaliveTimeout", AmneziaWgFieldLabel.Literal("KeepaliveTimeout"), AmneziaWg::keepaliveTimeout),
    AmneziaWgEditorField("maxHandshakeAttempts", AmneziaWgFieldLabel.Literal("MaxHandshakeAttempts"), AmneziaWg::maxHandshakeAttempts),
    AmneziaWgEditorField("persistentKeepalive", AmneziaWgFieldLabel.Literal("PersistentKeepalive"), AmneziaWg::persistentKeepalive),
    AmneziaWgEditorField("randomTrailers", AmneziaWgFieldLabel.Literal("RandomTrailers"), AmneziaWg::randomTrailers),
    AmneziaWgEditorField("disableCookies", AmneziaWgFieldLabel.Literal("DisableCookies"), AmneziaWg::disableCookies),
)

/**
 * Every field is its own list item. The editor has about forty fields, and one item per section made the lazy
 * list compose and measure all of them at once, which stuttered while scrolling and when the keyboard opened.
 * A field that scrolls away is disposed and drawn again from the profile, which always holds the latest text.
 */
fun LazyListScope.amneziaWgProxyServer(awgEdit: AmneziaWg) {
    item(key = "properties") {
        SmallTitle(text = stringResource(Res.string.proxy_editor_properties))
    }
    amneziaWgPropertyFields.forEach { field ->
        item(key = field.key) { AmneziaWgTextField(awgEdit, field) }
    }
    item(key = "obfuscation") {
        SmallTitle(text = stringResource(Res.string.proxy_editor_amnezia_obfuscation))
    }
    amneziaWgObfuscationFields.forEach { field ->
        item(key = field.key) { AmneziaWgTextField(awgEdit, field) }
    }
    item(key = "finalMask") {
        val focusManager = LocalFocusManager.current
        TextField(
            label = stringResource(Res.string.proxy_editor_final_mask),
            state = rememberTextFieldState(initialText = awgEdit.finalMask),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 5, maxHeightInLines = 20),
            inputTransformation = InputTransformation {
                awgEdit.finalMask = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun AmneziaWgTextField(awgEdit: AmneziaWg, field: AmneziaWgEditorField) {
    val focusManager = LocalFocusManager.current
    val label = when (val label = field.label) {
        is AmneziaWgFieldLabel.Resource -> stringResource(label.resource)
        is AmneziaWgFieldLabel.Literal -> label.text
    }
    TextField(
        label = label,
        state = rememberTextFieldState(initialText = field.read(awgEdit)),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation {
            if (field.digitsOnly && !asCharSequence().isDigitsOnly()) {
                revertAllChanges()
                return@InputTransformation
            }
            field.write(awgEdit, asCharSequence().toString())
        },
        modifier = Modifier.padding(bottom = 12.dp),
        onKeyboardAction = { focusManager.clearFocus() },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}
