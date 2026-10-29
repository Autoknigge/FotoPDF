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
import kotlin.math.min

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
    fun toJpeg(src: Bitmap, userRotation: Int, quality: Int, filter: Int = 0): JpegResult {
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
        if (bmp !== src) bmp.recycle()
        bmp = src

        val out = applyFilter(flat, filter)
        val baos = ByteArrayOutputStream(max(64 * 1024, flat.width * flat.height / 8))
        out.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val result = JpegResult(baos.toByteArray(), out.width, out.height)

        if (out !== flat) out.recycle()
        flat.recycle()
        return result
    }

    /**
     * Farbfilter für Dokumente: 1 = Graustufen mit mehr Kontrast und hellerem Papier,
     * 2 = Schwarzweiß mit örtlicher Schwelle (gleicht Schatten und ungleiches Licht aus).
     * Gibt [src] unverändert zurück (Filter 0) oder ein neues Bild.
     */
    private fun applyFilter(src: Bitmap, filter: Int): Bitmap {
        if (filter == 0) return src
        val w = src.width
        val h = src.height
        val gray = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val gc = Canvas(gray)
        val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        if (filter == 1) {
            val scale = 1.55f
            val shift = (1f - scale) / 2f * 255f + 28f
            cm.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, shift,
                        0f, scale, 0f, 0f, shift,
                        0f, 0f, scale, 0f, shift,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        gc.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        })
        if (filter == 1) return gray

        // Schwarzweiß: Hintergrundhelligkeit aus stark verkleinerter Kopie schätzen, dann je Pixel vergleichen.
        val small = Bitmap.createScaledBitmap(gray, max(1, w / 16), max(1, h / 16), true)
        val bg = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        val rowsPerBand = 48
        val a = IntArray(w * rowsPerBand)
        val b = IntArray(w * rowsPerBand)
        var y = 0
        while (y < h) {
            val rows = min(rowsPerBand, h - y)
            gray.getPixels(a, 0, w, 0, y, w, rows)
            bg.getPixels(b, 0, w, 0, y, w, rows)
            for (i in 0 until w * rows) {
                val g = (a[i] shr 8) and 0xFF
                val m = (b[i] shr 8) and 0xFF
                a[i] = if (g < m * 0.86f) Color.BLACK else Color.WHITE
            }
            gray.setPixels(a, 0, w, 0, y, w, rows)
            y += rows
        }
        bg.recycle()
        return gray
    }
}
