// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.clipboard.setPlainText
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

/** A comprehensive reference for the deep links handled by SKIPI. */
@Composable
fun SkipiUrlSchemesPage(
    padding: PaddingValues,
) {
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val clipboard = LocalClipboard.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.common_copied)
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    fun copyCommand(command: String) {
        scope.launch {
            clipboard.setPlainText(command)
            tipNotifier.show(copiedMessage)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.settings_url_schemes),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = navigator::pop) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.background)
                    .pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                // 1. Note Banner
                item(key = "url_schemes_note") {
                    SchemesNoteCard()
                }

                // 2. Start Tunnel Group
                item(key = "group_start_title") {
                    SmallTitle(text = stringResource(R.string.url_schemes_group_start).uppercase())
                }
                item(key = "group_start_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SchemeChip(
                                command = "skipi://connect",
                                description = stringResource(R.string.url_scheme_connect_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://open",
                                description = stringResource(R.string.url_scheme_open_desc),
                                onCopy = ::copyCommand,
                            )
                        }
                    }
                }

                // 3. Stop Connection Group
                item(key = "group_stop_title") {
                    SmallTitle(text = stringResource(R.string.url_schemes_group_stop).uppercase())
                }
                item(key = "group_stop_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SchemeChip(
                                command = "skipi://disconnect",
                                description = stringResource(R.string.url_scheme_disconnect_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://close",
                                description = stringResource(R.string.url_scheme_close_desc),
                                onCopy = ::copyCommand,
                            )
                        }
                    }
                }

                // 4. Toggle Connection Group
                item(key = "group_toggle_title") {
                    SmallTitle(text = stringResource(R.string.url_schemes_group_toggle).uppercase())
                }
                item(key = "group_toggle_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SchemeChip(
                                command = "skipi://toggle",
                                description = stringResource(R.string.url_scheme_toggle_desc),
                                onCopy = ::copyCommand,
                            )
                        }
                    }
                }

                // 5. Add Configuration Group
                item(key = "group_add_title") {
                    SmallTitle(text = stringResource(R.string.url_schemes_group_add).uppercase())
                }
                item(key = "group_add_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SchemeChip(
                                command = "skipi://import/{base64}",
                                description = stringResource(R.string.url_scheme_import_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://add/{url}",
                                description = stringResource(R.string.url_scheme_add_url_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://add/{base64}",
                                description = stringResource(R.string.url_scheme_add_encoded_desc),
                                onCopy = ::copyCommand,
                            )
                        }
                    }
                }

                // 6. Routing Group
                item(key = "group_routing_title") {
                    SmallTitle(text = stringResource(R.string.url_schemes_group_routing).uppercase())
                }
                item(key = "group_routing_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SchemeChip(
                                command = "skipi://routing/add/{base64}",
                                description = stringResource(R.string.url_scheme_routing_add_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://routing/onadd/{base64}",
                                description = stringResource(R.string.url_scheme_routing_onadd_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://conf/add/{base64}",
                                description = stringResource(R.string.url_scheme_conf_add_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "skipi://conf/onadd/{base64}",
                                description = stringResource(R.string.url_scheme_conf_onadd_desc),
                                onCopy = ::copyCommand,
                            )
                        }
                    }
                }

                // 7. Third-Party Formats Group
                item(key = "group_external_title") {
                    SmallTitle(text = stringResource(R.string.url_schemes_group_external).uppercase())
                }
                item(key = "group_external_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SchemeChip(
                                command = "v2rayng://install-sub?url={url}",
                                description = stringResource(R.string.url_scheme_v2rayng_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "clash://install-config?url={url}",
                                description = stringResource(R.string.url_scheme_clash_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "clashmeta://install-config?url={url}",
                                description = stringResource(R.string.url_scheme_clashmeta_desc),
                                onCopy = ::copyCommand,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SchemeChip(
                                command = "flclashx://install-config?url={url}",
                                description = stringResource(R.string.url_scheme_flclash_desc),
                                onCopy = ::copyCommand,
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun SchemesNoteCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
            contentColor = AppTheme.colors.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.url_schemes_note_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.url_schemes_note_desc),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun SchemeChip(
    command: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable { onCopy(command) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = command,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }

        IconButton(
            onClick = { onCopy(command) },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = stringResource(R.string.common_copy),
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
