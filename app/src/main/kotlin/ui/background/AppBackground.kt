// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.LocalAppStateStore
import app.collectAppState
import app.modes.BackgroundStyleClassic
import app.modes.BackgroundStyleConnection
import app.modes.BackgroundStylePhoto
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.AppTheme

private const val BackgroundPhotoFileName = "custom_background.jpg"
private const val MaxPhotoDimension = 2048

var backgroundPhotoUpdateSeed by mutableLongStateOf(0L)
    private set

fun customBackgroundPhotoFile(context: Context): File =
    File(context.filesDir, BackgroundPhotoFileName)

fun customBackgroundPhotoExists(context: Context): Boolean =
    customBackgroundPhotoFile(context).exists()

fun customBackgroundPhotoPath(context: Context): String? {
    val file = customBackgroundPhotoFile(context)
    return if (file.exists()) file.absolutePath else null
}

suspend fun saveCustomBackgroundPhoto(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching false
            if (bytes.isEmpty()) return@runCatching false

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return@runCatching false

            var sampleSize = 1
            while (width / sampleSize > MaxPhotoDimension || height / sampleSize > MaxPhotoDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return@runCatching false

            val targetFile = customBackgroundPhotoFile(context)
            FileOutputStream(targetFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            withContext(Dispatchers.Main.immediate) {
                backgroundPhotoUpdateSeed = System.currentTimeMillis()
            }
            true
        }.getOrDefault(false)
    }

fun clearCustomBackgroundPhoto(context: Context): Boolean {
    val file = customBackgroundPhotoFile(context)
    val deleted = if (file.exists()) file.delete() else true
    backgroundPhotoUpdateSeed = System.currentTimeMillis()
    return deleted
}

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val context = LocalContext.current
    val photoSeed = backgroundPhotoUpdateSeed

    val isDark = AppTheme.colors.isDark
    val solidFallback = AppTheme.colors.background

    when (appState.backgroundStyle) {
        BackgroundStylePhoto -> {
            val photoBitmap: ImageBitmap? = remember(photoSeed, appState.backgroundStyle) {
                val file = customBackgroundPhotoFile(context)
                if (file.exists()) {
                    runCatching {
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    }.getOrNull()
                } else {
                    null
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(solidFallback),
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    val dimPercent = appState.backgroundPhotoDimPercent.coerceIn(0, 100)
                    if (dimPercent > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = dimPercent / 100f)),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        if (isDark) Color(0xFF312E81) else Color(0xFFC7D2FE),
                                        if (isDark) Color(0xFF1E1B4B) else Color(0xFFE0E7FF),
                                        solidFallback,
                                    ),
                                ),
                            ),
                    )
                }
                content()
            }
        }

        BackgroundStyleConnection -> {
            val isRunning = appState.proxyRunning
            val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.90f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseAlpha",
            )

            val activeColor1 by animateColorAsState(
                targetValue = if (isRunning) Color(0xFF10B981) else (if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)),
                animationSpec = tween(durationMillis = 500),
                label = "activeColor1",
            )
            val activeColor2 by animateColorAsState(
                targetValue = if (isRunning) Color(0xFF06B6D4) else (if (isDark) Color(0xFF8B5CF6) else Color(0xFF7C3AED)),
                animationSpec = tween(durationMillis = 500),
                label = "activeColor2",
            )

            val primaryAlpha = (if (isRunning) (if (isDark) 0.58f else 0.40f) else (if (isDark) 0.45f else 0.30f)) * pulse
            val secondaryAlpha = (if (isRunning) (if (isDark) 0.32f else 0.20f) else (if (isDark) 0.22f else 0.14f)) * pulse

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(solidFallback)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                activeColor1.copy(alpha = primaryAlpha),
                                activeColor2.copy(alpha = secondaryAlpha),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = 1400f,
                        ),
                    ),
            ) {
                content()
            }
        }

        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(solidFallback),
            ) {
                content()
            }
        }
    }
}
