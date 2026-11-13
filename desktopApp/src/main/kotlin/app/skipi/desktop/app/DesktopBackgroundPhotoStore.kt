// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * Desktop counterpart of Android's custom background photo: one JPEG (quality 92) whose longest
 * side never exceeds [MaxPhotoDimension]. A failed save leaves the previous photo untouched.
 */
internal class DesktopBackgroundPhotoStore(private val directory: Path) {
    fun file(): Path = directory.resolve(FileName)

    fun exists(): Boolean = file().exists() && Files.size(file()) > 0

    fun save(source: Path): Boolean = runCatching {
        val image = readScaledToLimit(source) ?: return false
        Files.createDirectories(directory)
        val temp = directory.resolve(TempFileName)
        writeJpeg(image, temp)
        if (!temp.exists() || Files.size(temp) == 0L) return false
        Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)

    fun clear(): Boolean = runCatching { file().deleteIfExists(); true }.getOrDefault(false)

    /**
     * Decodes with source subsampling, so a 48-megapixel photo is never held at full size, then draws the
     * result once into the final RGB image (no full-size intermediate copy).
     */
    private fun readScaledToLimit(source: Path): BufferedImage? {
        ImageIO.createImageInputStream(source.toFile()).use { input ->
            if (input == null) return null
            val reader = ImageIO.getImageReaders(input).takeIf { it.hasNext() }?.next() ?: return null
            try {
                reader.setInput(input, true, true)
                val sourceLongest = maxOf(reader.getWidth(0), reader.getHeight(0))
                val step = (sourceLongest / MaxPhotoDimension).coerceAtLeast(1)
                val params = reader.defaultReadParam.apply { if (step > 1) setSourceSubsampling(step, step, 0, 0) }
                val decoded = reader.read(0, params)
                return decoded.scaledToLimit()
            } finally {
                reader.dispose()
            }
        }
    }

    private fun BufferedImage.scaledToLimit(): BufferedImage {
        val longest = maxOf(width, height)
        val scale = if (longest <= MaxPhotoDimension) 1.0 else MaxPhotoDimension.toDouble() / longest
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB).also { target ->
            target.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(this@scaledToLimit, 0, 0, targetWidth, targetHeight, null)
                dispose()
            }
        }
    }

    private fun writeJpeg(image: BufferedImage, target: Path) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JpegQuality
            }
            ImageIO.createImageOutputStream(target.toFile()).use { output ->
                writer.output = output
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
    }

    private companion object {
        const val FileName = "custom_background.jpg"
        const val TempFileName = "custom_background_tmp.jpg"
        const val MaxPhotoDimension = 2560
        const val JpegQuality = 0.92f
    }
}

/** The application's photo store plus a Compose-observable version bumped whenever the file changes. */
internal object DesktopBackgroundPhoto {
    val store: DesktopBackgroundPhotoStore by lazy {
        DesktopBackgroundPhotoStore(DesktopSettingsLibraries.defaultPath().parent)
    }
    private val versionState = androidx.compose.runtime.mutableLongStateOf(0L)
    val version: Long get() = versionState.longValue

    fun exists(): Boolean = store.exists()

    fun save(source: Path): Boolean = store.save(source).also { if (it) versionState.longValue = System.nanoTime() }

    fun clear(): Boolean = store.clear().also { versionState.longValue = System.nanoTime() }

    fun choose(): Path? {
        val chooser = javax.swing.JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "bmp", "gif")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.toPath()
    }
}
