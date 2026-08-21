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
    val solidFallback = if (isDark) Color(0xFF16171A) else Color(0xFFF5F6F8)

    when (appState.backgroundStyle) {
        BackgroundStylePhoto -> {
            val photoBitmap by produceState<ImageBitmap?>(
                initialValue = null,
                key1 = photoSeed,
                key2 = appState.backgroundStyle,
            ) {
                value = withContext(Dispatchers.IO) {
                    val file = customBackgroundPhotoFile(context)
                    if (file.exists()) {
                        runCatching {
                            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                        }.getOrNull()
                    } else {
                        null
                    }
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(solidFallback),
            ) {
                val currentBitmap = photoBitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val placeholderColor1 = if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)
                        val placeholderColor2 = if (isDark) Color(0xFF1E1B4B) else Color(0xFFF3F4F6)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    placeholderColor1.copy(alpha = if (isDark) 0.35f else 0.45f),
                                    placeholderColor2.copy(alpha = if (isDark) 0.15f else 0.20f),
                                    solidFallback,
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.3f),
                                radius = size.maxDimension * 0.8f,
                            ),
                        )
                    }
                }
                content()
            }
        }

        BackgroundStyleConnection -> {
            val isRunning = appState.proxyRunning
            val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.88f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseAlpha",
            )

            val activeColor1 by animateColorAsState(
                targetValue = if (isRunning) Color(0xFF10B981) else (if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)),
                animationSpec = tween(durationMillis = 600),
                label = "activeColor1",
            )
            val activeColor2 by animateColorAsState(
                targetValue = if (isRunning) Color(0xFF06B6D4) else (if (isDark) Color(0xFF3B82F6) else Color(0xFF60A5FA)),
                animationSpec = tween(durationMillis = 600),
                label = "activeColor2",
            )

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(solidFallback),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (isRunning) {
                        val primaryAlpha = (if (isDark) 0.62f else 0.42f) * pulse
                        val secondaryAlpha = (if (isDark) 0.38f else 0.24f) * pulse
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeColor1.copy(alpha = primaryAlpha),
                                    activeColor2.copy(alpha = secondaryAlpha),
                                    solidFallback,
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.22f),
                                radius = size.maxDimension * 0.65f * pulse,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeColor2.copy(alpha = (if (isDark) 0.25f else 0.15f) * pulse),
                                    solidFallback,
                                ),
                                center = Offset(size.width * 0.75f, size.height * 0.75f),
                                radius = size.maxDimension * 0.5f,
                            ),
                        )
                    } else {
                        val idleAlpha = (if (isDark) 0.48f else 0.32f) * pulse
                        val idleSecondaryAlpha = (if (isDark) 0.25f else 0.16f) * pulse
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeColor1.copy(alpha = idleAlpha),
                                    activeColor2.copy(alpha = idleSecondaryAlpha),
                                    solidFallback,
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.22f),
                                radius = size.maxDimension * 0.65f * pulse,
                            ),
                        )
                    }
                }
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
