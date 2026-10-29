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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    errorMessage: String? = null,
    onDraftChanged: () -> Unit = {},
) {
    if (!show) return

    var draft by remember(initialData) { mutableStateOf(initialData) }

    fun updateDraft(transform: (SkipiSubscriptionEditData) -> SkipiSubscriptionEditData) {
        draft = transform(draft)
        onDraftChanged()
    }

    AppWindowDialog(
        show = show,
        title = stringResource(Res.string.subscription_edit),
        onDismissRequest = onDismiss,
    ) {
        // Phone-sized type: the default 17sp field text made this dialog tall and crowded.
        val textFieldStyle = MiuixTheme.textStyles.main.copy(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = draft.name,
                onValueChange = { value -> updateDraft { it.copy(name = value) } },
                label = stringResource(Res.string.subscription_group_name),
                modifier = Modifier.fillMaxWidth(),
                textStyle = textFieldStyle,
                singleLine = true,
            )

            TextField(
                value = draft.url,
                onValueChange = { value -> updateDraft { it.copy(url = value) } },
                label = stringResource(Res.string.subscription_url),
                modifier = Modifier.fillMaxWidth(),
                textStyle = textFieldStyle,
            )

            if (draft.url.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    text = stringResource(Res.string.home_subscription_insecure_http),
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    SubscriptionToggleRow(
                        title = stringResource(Res.string.home_subscription_enabled_toggle),
                        hint = stringResource(Res.string.home_subscription_auto_update_toggle_hint),
                        checked = draft.enabled,
                        onCheckedChange = { value -> updateDraft { it.copy(enabled = value) } },
                    )
                    HorizontalDivider()
                    SubscriptionToggleRow(
                        title = stringResource(Res.string.subscription_auto_override_rules),
                        hint = stringResource(Res.string.subscription_auto_override_rules_summary),
                        checked = draft.autoOverrideRules,
                        onCheckedChange = { value -> updateDraft { it.copy(autoOverrideRules = value) } },
                    )
                    HorizontalDivider()
                    SubscriptionToggleRow(
                        title = stringResource(Res.string.subscription_update_via_proxy),
                        hint = stringResource(Res.string.subscription_update_via_proxy_summary),
                        checked = draft.updateViaProxy,
                        onCheckedChange = { value -> updateDraft { it.copy(updateViaProxy = value) } },
                    )
                }
            }

            if (draft.url.isNotBlank()) {
                TextField(
                    value = draft.userAgent,
                    onValueChange = { value -> updateDraft { it.copy(userAgent = value) } },
                    label = stringResource(Res.string.subscription_user_agent),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = textFieldStyle,
                    singleLine = true,
                )

                TextField(
                    value = draft.ageSecretKey,
                    onValueChange = { value -> updateDraft { it.copy(ageSecretKey = value) } },
                    label = stringResource(Res.string.subscription_age_secret_key),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = textFieldStyle,
                    singleLine = true,
                )
            }

            errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
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
                    Text(text = stringResource(Res.string.subscription_delete), fontSize = 14.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f),
                    enabled = draft.name.isNotBlank(),
                ) {
                    Text(text = stringResource(Res.string.common_save), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
