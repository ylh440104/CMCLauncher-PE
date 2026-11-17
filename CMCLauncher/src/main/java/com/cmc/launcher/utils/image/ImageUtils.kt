package com.cmc.launcher.utils.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import coil3.decode.DecodeUtils
import coil3.gif.isGif
import com.cmc.launcher.utils.logging.Logger
import okio.buffer
import okio.source
import java.io.File

private const val TAG = "ImageUtils"

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap
    }

    val width = if (intrinsicWidth > 0) intrinsicWidth else 1
    val height = if (intrinsicHeight > 0) intrinsicHeight else 1

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}

inline fun Bitmap.isColorMatch(
    xRange: IntRange,
    yRange: IntRange,
    predicate: (color: Int, x: Int, y: Int) -> Boolean,
    requireAll: Boolean = false
): Boolean {
    val width = this.width
    val height = this.height

    for (x in xRange) {
        if (x !in 0 until width) continue
        for (y in yRange) {
            if (y !in 0 until height) continue
            val match = predicate(this[x, y], x, y)
            if (requireAll) {
                if (!match) return false
            } else {
                if (match) return true
            }
        }
    }
    return requireAll
}

fun Bitmap?.recycleIfLarge(thresholdBytes: Int = 8 * 1024 * 1024) {
    this ?: return

    if (!isRecycled && byteCount >= thresholdBytes) {
        recycle()
    }
}

fun File.isImageFile(): Boolean {
    if (!this.exists()) return false

    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(this.absolutePath, options)
        options.outWidth > 0 && options.outHeight > 0
    } catch (e: Exception) {
        Logger.warning(TAG,
            "An exception occurred while trying to determine if ${this.absolutePath} is an image.",
            e
        )
        false
    }
}

fun File.isGifFile(): Boolean {
    if (!this.exists()) return false

    return try {
        source().buffer().use { DecodeUtils.isGif(it) }
    } catch (e: Exception) {
        Logger.warning(TAG,
            "An exception occurred while trying to determine if ${this.absolutePath} is a gif.",
            e
        )
        false
    }
}
