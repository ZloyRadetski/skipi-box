// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import app.skipi.ui.server.editor.editableCopy as sharedEditableCopy
import app.skipi.ui.server.editor.editorTitle as sharedEditorTitle
import app.skipi.ui.server.editor.proxyServerEditorContent as sharedProxyServerEditorContent
import features.proxy.server.model.ProxyServer

typealias ProxyServerEditorOptions = app.skipi.ui.server.editor.ProxyServerEditorOptions
typealias ProxyServerEditorGroupOption = app.skipi.ui.server.editor.ProxyServerEditorGroupOption
typealias ProxyServerEditorMemberOption = app.skipi.ui.server.editor.ProxyServerEditorMemberOption

fun ProxyServer<*>.editableCopy(): ProxyServer<*> = sharedEditableCopy()

@Composable
fun ProxyServer<*>.editorTitle(): String = sharedEditorTitle()

fun LazyListScope.proxyServerEditorContent(
    proxyServer: ProxyServer<*>,
    options: ProxyServerEditorOptions,
) = sharedProxyServerEditorContent(proxyServer, options)
