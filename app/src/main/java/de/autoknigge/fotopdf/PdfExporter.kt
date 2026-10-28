package de.autoknigge.fotopdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.BufferedOutputStream
import kotlin.math.min

class ExportResult(val uri: Uri, val fileName: String, val pages: Int, val skipped: Int, val custom: Boolean)

object PdfExporter {

    private const val A4_W = 595f
    private const val A4_H = 842f
    private const val MARGIN = 28f
    private const val PT_PER_PX = 72f / 150f // „Bildgröße“: Seite entspricht 150 dpi

    fun export(
        ctx: Context,
        items: List<PhotoItem>,
        a4: Boolean,
        maxSide: Int,
        jpegQuality: Int,
        fileName: String,
        customTarget: Uri?,
        onProgress: (Int) -> Unit
    ): ExportResult {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = customTarget
            ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Datei konnte nicht angelegt werden.")

        try {
            var pages = 0
            var skipped = 0

            val stream = resolver.openOutputStream(target)
                ?: throw IllegalStateException("Datei konnte nicht geschrieben werden.")
            BufferedOutputStream(stream, 64 * 1024).use { out ->
                val pdf = PdfWriter(out)

                items.forEachIndexed { index, item ->
                    val bmp = try {
                        Photos.loadOriented(ctx, item.uri, maxSide)
                    } catch (e: Exception) {
                        null
                    } catch (e: OutOfMemoryError) {
                        null
                    }

                    if (bmp == null) {
                        skipped++
                    } else {
                        try {
                            val jpg = Photos.toJpeg(bmp, item.rotation, jpegQuality)
                            addPage(pdf, jpg, a4)
                            pages++
                        } finally {
                            bmp.recycle()
                        }
                    }
                    onProgress(index + 1)
                }

                if (pages == 0) throw IllegalStateException("Keines der Fotos konnte gelesen werden.")
                pdf.finish()
            }

            if (customTarget == null) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(target, done, null, null)
            }

            return ExportResult(target, fileName, pages, skipped, customTarget != null)
        } catch (t: Throwable) {
            try {
                if (customTarget != null) {
                    DocumentsContract.deleteDocument(resolver, target)
                } else {
                    resolver.delete(target, null, null)
                }
            } catch (ignored: Exception) {
            }
            throw t
        }
    }

    private fun addPage(pdf: PdfWriter, jpg: JpegResult, a4: Boolean) {
        val w = jpg.width.toFloat()
        val h = jpg.height.toFloat()

        if (a4) {
            val landscape = w > h
            val pw = if (landscape) A4_H else A4_W
            val ph = if (landscape) A4_W else A4_H
            val s = min((pw - 2 * MARGIN) / w, (ph - 2 * MARGIN) / h)
            val dw = w * s
            val dh = h * s
            pdf.addPage(jpg.bytes, jpg.width, jpg.height, pw, ph, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
        } else {
            val pw = (w * PT_PER_PX).coerceIn(72f, 14400f)
            val ph = (h * PT_PER_PX).coerceIn(72f, 14400f)
            pdf.addPage(jpg.bytes, jpg.width, jpg.height, pw, ph, 0f, 0f, pw, ph)
        }
    }
}
