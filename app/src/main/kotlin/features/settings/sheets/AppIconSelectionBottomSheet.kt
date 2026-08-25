// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.animation.AnimatedVisibility
import ui.components.AppWindowBottomSheet
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import app.modes.AppIconCyber
import app.modes.AppIconDark
import app.modes.AppIconDefault
import app.modes.AppIconEmerald
import app.modes.AppIconLight
import app.modes.AppIconMonet
import app.modes.AppIconNordic
import app.modes.AppIconStealth
import app.modes.AppIconSunset
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

internal enum class AppIconDescriptor(
    val mode: Int,
    val titleRes: Int,
    val backgroundDrawableRes: Int? = null,
    val isMonet: Boolean = false,
    val foregroundDrawableRes: Int,
) {
    Default(
        mode = AppIconDefault,
        titleRes = R.string.app_icon_default,
        backgroundDrawableRes = R.drawable.ic_launcher_background,
        foregroundDrawableRes = R.drawable.ic_launcher_foreground,
    ),
    Dark(
        mode = AppIconDark,
        titleRes = R.string.app_icon_dark,
        backgroundDrawableRes = R.drawable.ic_launcher_dark_background,
        foregroundDrawableRes = R.drawable.ic_launcher_foreground,
    ),
    Light(
        mode = AppIconLight,
        titleRes = R.string.app_icon_light,
        backgroundDrawableRes = R.drawable.ic_launcher_light_background,
        foregroundDrawableRes = R.drawable.ic_launcher_light_foreground,
    ),
    Monet(
        mode = AppIconMonet,
        titleRes = R.string.app_icon_monet,
        isMonet = true,
        foregroundDrawableRes = R.drawable.ic_launcher_monet_foreground,
    ),
    Cyber(
        mode = AppIconCyber,
        titleRes = R.string.app_icon_cyber,
        backgroundDrawableRes = R.drawable.ic_launcher_cyber_background,
        foregroundDrawableRes = R.drawable.ic_launcher_cyber_foreground,
    ),
    Sunset(
        mode = AppIconSunset,
        titleRes = R.string.app_icon_sunset,
        backgroundDrawableRes = R.drawable.ic_launcher_sunset_background,
        foregroundDrawableRes = R.drawable.ic_launcher_foreground,
    ),
    Nordic(
        mode = AppIconNordic,
        titleRes = R.string.app_icon_nordic,
        backgroundDrawableRes = R.drawable.ic_launcher_nordic_background,
        foregroundDrawableRes = R.drawable.ic_launcher_foreground,
    ),
    Emerald(
        mode = AppIconEmerald,
        titleRes = R.string.app_icon_emerald,
        backgroundDrawableRes = R.drawable.ic_launcher_emerald_background,
        foregroundDrawableRes = R.drawable.ic_launcher_foreground,
    ),
    Stealth(
        mode = AppIconStealth,
        titleRes = R.string.app_icon_stealth,
        backgroundDrawableRes = R.drawable.ic_launcher_stealth_background,
        foregroundDrawableRes = R.drawable.ic_launcher_stealth_foreground,
    ),
}

internal fun appIconDescriptorForMode(mode: Int): AppIconDescriptor {
    return AppIconDescriptor.entries.firstOrNull { it.mode == mode } ?: AppIconDescriptor.Default
}

@Composable
internal fun AppIconPreviewBox(
    descriptor: AppIconDescriptor,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
) {
    val monetBgColor = MiuixTheme.colorScheme.primaryContainer
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (descriptor.backgroundDrawableRes != null) {
                    Modifier.paint(
                        painter = painterResource(descriptor.backgroundDrawableRes),
                        contentScale = ContentScale.Crop,
                    )
                } else if (descriptor.isMonet) {
                    Modifier.background(monetBgColor)
                } else {
                    Modifier.background(Color.Black)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(descriptor.foregroundDrawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun AppIconSelectionBottomSheet(
    show: Boolean,
    selectedIconMode: Int,
    onSelectIconMode: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppWindowBottomSheet(
        show = show,
        title = stringResource(R.string.app_icon),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = AppIconDescriptor.entries,
                key = { it.mode },
            ) { descriptor ->
                val isSelected = descriptor.mode == selectedIconMode
                val primaryColor = MiuixTheme.colorScheme.primary
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) primaryColor else Color.Transparent,
                    label = "icon_border_color",
                )
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1.0f,
                    label = "icon_scale",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelectIconMode(descriptor.mode)
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .size(72.dp)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) borderColor else MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconPreviewBox(
                            descriptor = descriptor,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 14.dp,
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .size(20.dp)
                                    .background(primaryColor, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(descriptor.titleRes),
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) primaryColor else MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
