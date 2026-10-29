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
import app.skipi.ui.background.SkipiAppBackground
import app.skipi.ui.anim.rememberInfinitePulse
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

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val context = LocalContext.current
    val photoSeed = backgroundPhotoUpdateSeed
    val photo: ImageBitmap? = if (appState.backgroundStyle == BackgroundStylePhoto) {
        remember(photoSeed) { loadCustomBackgroundBitmap(context) }
    } else {
        null
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    SkipiAppBackground(
        style = appState.backgroundStyle,
        isDark = AppTheme.colors.isDark,
        solidColor = AppTheme.colors.background,
        isConnected = appState.proxyRunning,
        dimPercent = appState.backgroundPhotoDimPercent,
        photo = photo,
        reduceMotion = powerManager?.isPowerSaveMode == true,
        modifier = modifier,
        content = content,
    )
}
