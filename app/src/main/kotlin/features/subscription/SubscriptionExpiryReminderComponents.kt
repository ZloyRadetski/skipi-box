// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ExpiryReminderUnit
import app.R
import app.SubscriptionExpiryReminder
import features.subscription.notification.formatLabel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme

@Composable
internal fun SubscriptionExpiryReminderList(
    reminders: List<SubscriptionExpiryReminder>,
    onRemindersChange: (List<SubscriptionExpiryReminder>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editingReminder by remember { mutableStateOf<Pair<Int, SubscriptionExpiryReminder>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (reminders.isEmpty()) {
            Text(
                text = stringResource(R.string.subscription_expiry_reminders_empty),
                style = MiuixTheme.textStyles.body2,
                color = AppTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        } else {
            reminders.forEachIndexed { index, reminder ->
                ReminderItemCard(
                    reminder = reminder,
                    onEdit = { editingReminder = index to reminder },
                    onDelete = {
                        val updated = reminders.toMutableList().apply { removeAt(index) }
                        onRemindersChange(updated)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.subscription_expiry_reminder_add),
                color = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }

    if (showAddDialog) {
        SubscriptionExpiryReminderDialog(
            initialReminder = SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days),
            title = stringResource(R.string.subscription_expiry_reminder_add),
            onDismiss = { showAddDialog = false },
            onConfirm = { newReminder ->
                val updated = (reminders + newReminder)
                    .distinctBy { it.totalSeconds }
                    .sortedByDescending { it.totalSeconds }
                onRemindersChange(updated)
                showAddDialog = false
            },
        )
    }

    editingReminder?.let { (index, reminder) ->
        SubscriptionExpiryReminderDialog(
            initialReminder = reminder,
            title = stringResource(R.string.subscription_expiry_reminder_edit),
            onDismiss = { editingReminder = null },
            onConfirm = { updatedReminder ->
                val list = reminders.toMutableList()
                list[index] = updatedReminder
                onRemindersChange(list.distinctBy { it.totalSeconds }.sortedByDescending { it.totalSeconds })
                editingReminder = null
            },
        )
    }
}

@Composable
private fun ReminderItemCard(
    reminder: SubscriptionExpiryReminder,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = AppTheme.colors.isDark
    val bg = if (isDark) Color(0xFF22232B) else Color(0xFFF3F4F6)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent.copy(alpha = if (isDark) 0.25f else 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Tune,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = reminder.formatLabel(context),
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = AppTheme.colors.onSurface,
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Close,
                contentDescription = stringResource(R.string.common_cancel),
                tint = AppTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SubscriptionExpiryReminderDialog(
    initialReminder: SubscriptionExpiryReminder,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (SubscriptionExpiryReminder) -> Unit,
) {
    val context = LocalContext.current
    var valueText by remember { mutableStateOf(initialReminder.value.toString()) }
    val units = remember {
        listOf(
            ExpiryReminderUnit.Days,
            ExpiryReminderUnit.Hours,
            ExpiryReminderUnit.Minutes,
            ExpiryReminderUnit.Weeks,
            ExpiryReminderUnit.AtExpiration,
        )
    }
    var selectedUnitIndex by remember {
        mutableIntStateOf(units.indexOf(initialReminder.unit).coerceAtLeast(0))
    }
    val selectedUnit = units[selectedUnitIndex]

    val unitItems = remember(units) {
        units.map { unit ->
            val label = when (unit) {
                ExpiryReminderUnit.AtExpiration -> context.getString(R.string.subscription_expiry_reminder_unit_at_expiration)
                ExpiryReminderUnit.Minutes -> context.getString(R.string.subscription_expiry_reminder_unit_minutes)
                ExpiryReminderUnit.Hours -> context.getString(R.string.subscription_expiry_reminder_unit_hours)
                ExpiryReminderUnit.Days -> context.getString(R.string.subscription_expiry_reminder_unit_days)
                ExpiryReminderUnit.Weeks -> context.getString(R.string.subscription_expiry_reminder_unit_weeks)
            }
            DropdownItem(text = label, summary = label)
        }
    }

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WindowSpinnerPreference(
                title = stringResource(R.string.subscription_expiry_reminder_value_label),
                summary = unitItems[selectedUnitIndex].summary,
                items = unitItems,
                selectedIndex = selectedUnitIndex,
                onSelectedIndexChange = { index -> selectedUnitIndex = index },
            )

            AnimatedVisibility(
                visible = selectedUnit != ExpiryReminderUnit.AtExpiration,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TextField(
                    state = rememberTextFieldState(initialText = valueText),
                    inputTransformation = InputTransformation {
                        valueText = asCharSequence().toString().filter { it.isDigit() }.take(4)
                    },
                    label = stringResource(R.string.subscription_expiry_reminder_value_label),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        val num = if (selectedUnit == ExpiryReminderUnit.AtExpiration) 0 else valueText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        onConfirm(SubscriptionExpiryReminder(num, selectedUnit))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
