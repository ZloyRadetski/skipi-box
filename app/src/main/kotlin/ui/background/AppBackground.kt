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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.LocalAppStateStore
import app.collectAppState
import app.modes.BackgroundStyleAurora
import app.modes.BackgroundStyleClassic
import app.modes.BackgroundStyleConnection
import app.modes.BackgroundStylePhoto
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.AppTheme

import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.os.Build
import android.os.PowerManager

private const val BackgroundPhotoFileName = "custom_background.jpg"
private const val MaxPhotoDimension = 2560

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

fun loadCustomBackgroundBitmap(context: Context): ImageBitmap? {
    val file = customBackgroundPhotoFile(context)
    if (!file.exists() || file.length() == 0L) return null
    return runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        val displayMetrics = context.resources.displayMetrics
        val maxTarget = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels, MaxPhotoDimension)
        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxTarget && height / (sampleSize * 2) >= maxTarget) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        bitmap.asImageBitmap()
    }.getOrNull()
}

suspend fun saveCustomBackgroundPhoto(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeBitmapFromUri(context, uri) ?: return@runCatching false
            val targetFile = customBackgroundPhotoFile(context)
            val tempFile = File(context.filesDir, "custom_background_tmp.jpg")
            FileOutputStream(tempFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            bitmap.recycle()
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
            withContext(Dispatchers.Main.immediate) {
                backgroundPhotoUpdateSeed = System.currentTimeMillis()
            }
            true
        }.getOrDefault(false)
    }

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                var sampleSize = 1
                while (width / (sampleSize * 2) >= MaxPhotoDimension || height / (sampleSize * 2) >= MaxPhotoDimension) {
                    sampleSize *= 2
                }
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
        } else {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            while (width / (sampleSize * 2) >= MaxPhotoDimension || height / (sampleSize * 2) >= MaxPhotoDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val rotationDegrees = getExifRotation(context, uri)
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) {
                    decoded.recycle()
                }
                rotated
            } else {
                decoded
            }
        }
    }.getOrNull()
}

private fun getExifRotation(context: Context, uri: Uri): Int {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
}

fun clearCustomBackgroundPhoto(context: Context): Boolean {
    val file = customBackgroundPhotoFile(context)
    val deleted = if (file.exists()) file.delete() else true
    backgroundPhotoUpdateSeed = System.currentTimeMillis()
    return deleted
}

private fun DrawScope.drawAuroraMesh(
    isDark: Boolean,
    t1: Float,
    t2: Float,
    t3: Float,
    t4: Float,
) {
    val width = size.width
    val height = size.height
    val radius = maxOf(width, height) * 0.65f

    val blobColors = if (isDark) {
        listOf(
            Color(0xFF4F46E5),
            Color(0xFF7C3AED),
            Color(0xFF06B6D4),
            Color(0xFF10B981),
        )
    } else {
        listOf(
            Color(0xFF818CF8),
            Color(0xFFA78BFA),
            Color(0xFF22D3EE),
            Color(0xFF34D399),
        )
    }
    val blobAlphas = if (isDark) {
        listOf(0.40f, 0.34f, 0.30f, 0.22f)
    } else {
        listOf(0.50f, 0.42f, 0.38f, 0.28f)
    }

    val centers = listOf(
        Offset(width * (0.15f + 0.20f * t1), height * (0.10f + 0.16f * (1f - t2))),
        Offset(width * (0.85f - 0.22f * t2), height * (0.05f + 0.14f * t3)),
        Offset(width * (0.25f + 0.25f * (1f - t3)), height * (0.85f - 0.18f * t4)),
        Offset(width * (0.80f - 0.18f * t4), height * (0.90f - 0.22f * (1f - t1))),
    )

    centers.forEachIndexed { index, center ->
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    blobColors[index].copy(alpha = blobAlphas[index]),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            size = size,
        )
    }
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
                loadCustomBackgroundBitmap(context)
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

        BackgroundStyleAurora -> {
            val dimPercent = appState.backgroundPhotoDimPercent.coerceIn(0, 100)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSaveMode = powerManager?.isPowerSaveMode == true

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(solidFallback),
            ) {
                if (isPowerSaveMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawAuroraMesh(isDark, t1 = 0.50f, t2 = 0.35f, t3 = 0.65f, t4 = 0.45f)
                    }
                } else {
                    val auroraTransition = rememberInfiniteTransition(label = "auroraMesh")
                    val auroraT1 by auroraTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 17000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "auroraT1",
                    )
                    val auroraT2 by auroraTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 13000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "auroraT2",
                    )
                    val auroraT3 by auroraTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 21000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "auroraT3",
                    )
                    val auroraT4 by auroraTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "auroraT4",
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawAuroraMesh(isDark, auroraT1, auroraT2, auroraT3, auroraT4)
                    }
                }
                if (dimPercent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = dimPercent / 100f)),
                    )
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
