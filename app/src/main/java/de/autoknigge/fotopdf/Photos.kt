package de.autoknigge.fotopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class PhotoItem(val id: Long, val uri: Uri, var rotation: Int = 0)

class JpegResult(val bytes: ByteArray, val width: Int, val height: Int)

object Photos {

    /** Lädt das Foto, richtet es nach den EXIF-Daten aus und verkleinert es auf höchstens [maxSide] Pixel. */
    fun loadOriented(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = ctx.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
        }

        var result = decoded
        if (!m.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (rotated !== decoded) decoded.recycle()
            result = rotated
        }

        val longest = max(result.width, result.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                result,
                max(1, (result.width * s).toInt()),
                max(1, (result.height * s).toInt()),
                true
            )
            if (scaled !== result) result.recycle()
            result = scaled
        }
        return result
    }

    /** Dreht um [userRotation] Grad, legt das Bild auf weißen Grund und kodiert es als JPEG (ohne Metadaten). */
    fun toJpeg(src: Bitmap, userRotation: Int, quality: Int): JpegResult {
        var bmp = src
        val turn = ((userRotation % 360) + 360) % 360
        if (turn != 0) {
            val m = Matrix().apply { postRotate(turn.toFloat()) }
            bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }

        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val baos = ByteArrayOutputStream(max(64 * 1024, flat.width * flat.height / 8))
        flat.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val result = JpegResult(baos.toByteArray(), flat.width, flat.height)

        flat.recycle()
        if (bmp !== src) bmp.recycle()
        return result
    }
}
