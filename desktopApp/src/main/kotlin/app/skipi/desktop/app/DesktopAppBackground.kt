// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.skipi.ui.background.SkipiAppBackground
import app.skipi.ui.settings.AppearanceSettingValues
import app.skipi.ui.theme.SkipiTheme
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Desktop host of the shared page background, driven by the persisted appearance settings. */
@Composable
internal fun DesktopAppBackground(
    settings: DesktopAppSettings,
    isConnected: Boolean,
    content: @Composable () -> Unit,
) {
    val style = settings.backgroundStyle ?: AppearanceSettingValues.BackgroundStyleClassic
    val version = DesktopBackgroundPhoto.version
    val photo: ImageBitmap? = if (style == AppearanceSettingValues.BackgroundStylePhoto) {
        produceState<ImageBitmap?>(null, version) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val file = DesktopBackgroundPhoto.store.file().toFile()
                    if (file.exists()) ImageIO.read(file)?.toComposeImageBitmap() else null
                }.getOrNull()
            }
        }.value
    } else {
        null
    }
    SkipiAppBackground(
        style = style,
        isDark = SkipiTheme.colors.isDark,
        solidColor = SkipiTheme.colors.background,
        isConnected = isConnected,
        dimPercent = settings.backgroundPhotoDimPercent ?: 45,
        photo = photo,
        reduceMotion = false,
        content = content,
    )
}
