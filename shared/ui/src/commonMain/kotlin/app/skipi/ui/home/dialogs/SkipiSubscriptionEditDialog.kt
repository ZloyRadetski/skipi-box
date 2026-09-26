// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.home_subscription_auto_update_toggle_hint
import app.skipi.ui.resources.home_subscription_enabled_toggle
import app.skipi.ui.resources.home_subscription_insecure_http
import app.skipi.ui.resources.subscription_age_secret_key
import app.skipi.ui.resources.subscription_auto_override_rules
import app.skipi.ui.resources.subscription_auto_override_rules_summary
import app.skipi.ui.resources.subscription_delete
import app.skipi.ui.resources.subscription_edit
import app.skipi.ui.resources.subscription_group_name
import app.skipi.ui.resources.subscription_update_via_proxy
import app.skipi.ui.resources.subscription_update_via_proxy_summary
import app.skipi.ui.resources.subscription_url
import app.skipi.ui.resources.subscription_user_agent
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SkipiSubscriptionEditData(
    val name: String = "",
    val url: String = "",
    val userAgent: String = "",
    val updateInterval: String = "24",
    val hwid: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val autoOverrideRules: Boolean = true,
    val enabled: Boolean = true,
)

@Composable
fun SkipiSubscriptionEditDialog(
    show: Boolean,
    initialData: SkipiSubscriptionEditData,
    onSave: (SkipiSubscriptionEditData) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    if (!show) return

    var draft by remember(initialData) { mutableStateOf(initialData) }

    AppWindowDialog(
        show = show,
        title = stringResource(Res.string.subscription_edit),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = stringResource(Res.string.subscription_group_name),
                modifier = Modifier.fillMaxWidth(),
            )

            TextField(
                value = draft.url,
                onValueChange = { draft = draft.copy(url = it) },
                label = stringResource(Res.string.subscription_url),
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.url.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    text = stringResource(Res.string.home_subscription_insecure_http),
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.home_subscription_enabled_toggle),
                                fontWeight = themedFontWeight(FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.home_subscription_auto_update_toggle_hint),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.subscription_auto_override_rules),
                                fontWeight = themedFontWeight(FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.subscription_auto_override_rules_summary),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = draft.autoOverrideRules,
                            onCheckedChange = { draft = draft.copy(autoOverrideRules = it) },
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.subscription_update_via_proxy),
                                fontWeight = themedFontWeight(FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.subscription_update_via_proxy_summary),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = draft.updateViaProxy,
                            onCheckedChange = { draft = draft.copy(updateViaProxy = it) },
                        )
                    }
                }
            }

            if (draft.url.isNotBlank()) {
                TextField(
                    value = draft.userAgent,
                    onValueChange = { draft = draft.copy(userAgent = it) },
                    label = stringResource(Res.string.subscription_user_agent),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextField(
                    value = draft.ageSecretKey,
                    onValueChange = { draft = draft.copy(ageSecretKey = it) },
                    label = stringResource(Res.string.subscription_age_secret_key),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (onDelete != null) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error.copy(alpha = 0.15f),
                        contentColor = MiuixTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(Res.string.subscription_delete))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f),
                    enabled = draft.name.isNotBlank(),
                ) {
                    Text(text = stringResource(Res.string.common_save))
                }
            }
        }
    }
}
