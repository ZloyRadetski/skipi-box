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
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@runCatching false

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

            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@runCatching false

            val targetFile = customBackgroundPhotoFile(context)
            val tempFile = File(context.filesDir, "$BackgroundPhotoFileName.tmp")
            FileOutputStream(tempFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            if (tempFile.renameTo(targetFile) || (targetFile.delete() && tempFile.renameTo(targetFile))) {
                backgroundPhotoUpdateSeed = System.currentTimeMillis()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

fun clearCustomBackgroundPhoto(context: Context): Boolean {
    val file = customBackgroundPhotoFile(context)
    val deleted = if (file.exists()) file.delete() else true
    if (deleted) {
        backgroundPhotoUpdateSeed = System.currentTimeMillis()
    }
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
            val photoBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = photoSeed) {
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
                    .background(solidFallback)
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
                }
                content()
            }
        }

        BackgroundStyleConnection -> {
            val isRunning = appState.proxyRunning
            val accentColor by animateColorAsState(
                targetValue = if (isRunning) AppTheme.colors.accent else AppTheme.colors.surfaceVariant,
                label = "connectionAccent",
            )
            val baseBackground = solidFallback

            val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseAlpha",
            )

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(baseBackground)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (isRunning) {
                        val glowAlpha = if (isDark) 0.25f else 0.18f
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = glowAlpha * pulse),
                                    accentColor.copy(alpha = (glowAlpha * 0.4f) * pulse),
                                    baseBackground,
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.18f),
                                radius = size.maxDimension * 0.7f * pulse,
                            )
                        )
                    } else {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = if (isDark) 0.08f else 0.05f),
                                    baseBackground,
                                ),
                                startY = 0f,
                                endY = size.height * 0.5f,
                            )
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
                    .background(solidFallback)
            ) {
                content()
            }
        }
    }
}
