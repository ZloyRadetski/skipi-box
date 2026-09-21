// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppOverlayDropdownPreference
import app.skipi.ui.resources.*
import features.proxy.server.model.OlcRtc
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField

fun LazyListScope.olcRtcProxyServer(olcEdit: OlcRtc) {
    item(key = "properties") {
        val focusManager = LocalFocusManager.current

        val providerOptions = remember {
            val base = listOf("jitsi", "telemost", "wbstream")
            if (olcEdit.provider.isNotBlank() && olcEdit.provider !in base) {
                base + olcEdit.provider
            } else {
                base
            }
        }
        val providerIndex = remember {
            mutableIntStateOf(
                providerOptions.indexOf(olcEdit.provider).coerceAtLeast(0)
            )
        }

        val transportOptions = remember {
            val base = listOf("datachannel", "vp8channel", "seichannel", "videochannel")
            if (olcEdit.transport.isNotBlank() && olcEdit.transport !in base) {
                base + olcEdit.transport
            } else {
                base
            }
        }
        val transportIndex = remember {
            mutableIntStateOf(
                transportOptions.indexOf(olcEdit.transport).coerceAtLeast(0)
            )
        }

        val selectedTransport = transportOptions.getOrElse(transportIndex.intValue) { olcEdit.transport }.lowercase()

        SmallTitle(text = stringResource(Res.string.proxy_editor_properties))
        TextField(
            label = stringResource(Res.string.proxy_editor_remarks),
            state = rememberTextFieldState(initialText = olcEdit.remarks),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.remarks = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        AppOverlayDropdownPreference(
            title = stringResource(Res.string.proxy_editor_olcrtc_provider),
            items = providerOptions,
            selectedIndex = providerIndex.intValue,
            modifier = Modifier.padding(bottom = 12.dp),
            onSelectedIndexChange = { newIndex ->
                providerIndex.intValue = newIndex
                olcEdit.provider = providerOptions[newIndex]
            },
        )
        AppOverlayDropdownPreference(
            title = stringResource(Res.string.proxy_editor_olcrtc_transport),
            items = transportOptions,
            selectedIndex = transportIndex.intValue,
            modifier = Modifier.padding(bottom = 12.dp),
            onSelectedIndexChange = { newIndex ->
                transportIndex.intValue = newIndex
                olcEdit.transport = transportOptions[newIndex]
            },
        )
        TextField(
            label = stringResource(Res.string.proxy_editor_olcrtc_room_url),
            state = rememberTextFieldState(initialText = olcEdit.roomUrl),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.roomUrl = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(Res.string.proxy_editor_olcrtc_encryption_key),
            state = rememberTextFieldState(initialText = olcEdit.encryptionKey),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.encryptionKey = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        key(selectedTransport) {
            when (selectedTransport) {
                "vp8channel" -> {
                    SmallTitle(text = stringResource(Res.string.proxy_editor_olcrtc_vp8_options))
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_vp8_fps),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("vp8-fps", "fps")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("vp8-fps", asCharSequence().toString().ifBlank { null }, "fps")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_vp8_batch),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("vp8-batch", "batch")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("vp8-batch", asCharSequence().toString().ifBlank { null }, "batch")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_custom_payload),
                        state = rememberTextFieldState(initialText = olcEdit.getCustomPayload(OlcRtc.KNOWN_VP8_KEYS)),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            olcEdit.setCustomPayload(OlcRtc.KNOWN_VP8_KEYS, asCharSequence().toString().ifBlank { null })
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
                "seichannel" -> {
                    SmallTitle(text = stringResource(Res.string.proxy_editor_olcrtc_sei_options))
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_sei_fps),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("fps", "vp8-fps")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("fps", asCharSequence().toString().ifBlank { null }, "vp8-fps")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_sei_batch),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("batch", "vp8-batch")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("batch", asCharSequence().toString().ifBlank { null }, "vp8-batch")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_sei_frag),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("frag")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("frag", asCharSequence().toString().ifBlank { null })
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_sei_ack_ms),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("ack-ms", "ack_timeout_ms")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("ack-ms", asCharSequence().toString().ifBlank { null }, "ack_timeout_ms")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_custom_payload),
                        state = rememberTextFieldState(initialText = olcEdit.getCustomPayload(OlcRtc.KNOWN_SEI_KEYS)),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            olcEdit.setCustomPayload(OlcRtc.KNOWN_SEI_KEYS, asCharSequence().toString().ifBlank { null })
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
                "videochannel" -> {
                    SmallTitle(text = stringResource(Res.string.proxy_editor_olcrtc_video_options))
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_width),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-w", "width")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("video-w", asCharSequence().toString().ifBlank { null }, "width")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_height),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-h", "height")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("video-h", asCharSequence().toString().ifBlank { null }, "height")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_fps),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-fps", "fps")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("video-fps", asCharSequence().toString().ifBlank { null }, "fps")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_codec),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-codec", "codec")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            olcEdit.setPayloadParameter("video-codec", asCharSequence().toString().ifBlank { null }, "codec")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_qr_size),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-qr-size", "qr_size")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("video-qr-size", asCharSequence().toString().ifBlank { null }, "qr_size")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_qr_recovery),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-qr-recovery", "qr_recovery")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            olcEdit.setPayloadParameter("video-qr-recovery", asCharSequence().toString().ifBlank { null }, "qr_recovery")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_tile_module),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-tile-module", "tile_module")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("video-tile-module", asCharSequence().toString().ifBlank { null }, "tile_module")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_video_tile_rs),
                        state = rememberTextFieldState(initialText = olcEdit.getPayloadParameter("video-tile-rs", "tile_rs")),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            if (!asCharSequence().all { it.isDigit() }) {
                                revertAllChanges()
                                return@InputTransformation
                            }
                            olcEdit.setPayloadParameter("video-tile-rs", asCharSequence().toString().ifBlank { null }, "tile_rs")
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    TextField(
                        label = stringResource(Res.string.proxy_editor_olcrtc_custom_payload),
                        state = rememberTextFieldState(initialText = olcEdit.getCustomPayload(OlcRtc.KNOWN_VIDEO_KEYS)),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation {
                            olcEdit.setCustomPayload(OlcRtc.KNOWN_VIDEO_KEYS, asCharSequence().toString().ifBlank { null })
                        },
                        modifier = Modifier.padding(bottom = 12.dp),
                        onKeyboardAction = { focusManager.clearFocus() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
                else -> {
                    if (selectedTransport != "datachannel" || olcEdit.payload.isNotBlank()) {
                        TextField(
                            label = stringResource(Res.string.proxy_editor_olcrtc_payload_optional),
                            state = rememberTextFieldState(initialText = olcEdit.payload),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            inputTransformation = InputTransformation {
                                olcEdit.payload = asCharSequence().toString()
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                            onKeyboardAction = { focusManager.clearFocus() },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                }
            }
        }

        TextField(
            label = stringResource(Res.string.proxy_editor_olcrtc_socks_port),
            state = rememberTextFieldState(initialText = olcEdit.localSocksPort),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().all { it.isDigit() }) {
                    revertAllChanges()
                    return@InputTransformation
                }
                olcEdit.localSocksPort = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}
