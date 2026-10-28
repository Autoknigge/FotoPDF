package de.autoknigge.fotopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Zuschnitt-Ansicht.
 * Festes Seitenverhältnis: Der Ausschnitt steht fest, das Foto wird verschoben und mit zwei Fingern gezoomt.
 * „Frei“ (Seitenverhältnis 0): Das Foto steht fest, Ränder und Ausschnitt werden gezogen.
 * Der Rahmen wird innen am Rand des Ausschnitts gezeichnet, das Ergebnis entspricht also der Vorschau.
 */
class CropView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {

    private var bmp: Bitmap? = null
    private val m = Matrix()
    private val crop = RectF()
    private var aspect = 1f // Breite/Höhe; 0 = frei

    private var borderFraction = 0f
    private var borderColor = Color.WHITE

    private val dens = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint().apply { color = 0xB3000000.toInt() }
    private val fillPaint = Paint()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD9B954.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * dens
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1f * dens
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9B954.toInt() }

    // Verschieben / Zoomen
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoom(d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    // Freier Zuschnitt
    private var actL = false
    private var actR = false
    private var actT = false
    private var actB = false
    private var moveAll = false

    fun setBitmap(b: Bitmap) {
        bmp = b
        layoutCrop()
    }

    fun setAspect(a: Float) {
        aspect = a
        layoutCrop()
    }

    fun setBorder(fraction: Float, color: Int) {
        borderFraction = fraction
        borderColor = color
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCrop()
    }

    private fun layoutCrop() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        val margin = 16f * dens
        val availW = width - 2 * margin
        val availH = height - 2 * margin
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()

        if (aspect > 0f) {
            val cw: Float
            val ch: Float
            if (availW / availH > aspect) {
                ch = availH; cw = availH * aspect
            } else {
                cw = availW; ch = availW / aspect
            }
            val cx = width / 2f
            val cy = height / 2f
            crop.set(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
            val s = max(cw / bw, ch / bh)
            m.setScale(s, s)
            m.postTranslate(cx - bw * s / 2f, cy - bh * s / 2f)
        } else {
            val s = min(availW / bw, availH / bh)
            m.setScale(s, s)
            m.postTranslate(width / 2f - bw * s / 2f, height / 2f - bh * s / 2f)
            crop.set(imageRect())
        }
        invalidate()
    }

    private fun imageRect(): RectF {
        val b = bmp ?: return RectF()
        val r = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
        m.mapRect(r)
        return r
    }

    private fun currentScale(): Float {
        val v = FloatArray(9)
        m.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun coverScale(): Float {
        val b = bmp ?: return 1f
        return max(crop.width() / b.width, crop.height() / b.height)
    }

    private fun zoom(factor: Float, fx: Float, fy: Float) {
        if (aspect <= 0f || bmp == null) return
        val cur = currentScale()
        val minS = coverScale()
        val target = (cur * factor).coerceIn(minS, minS * 8f)
        val f = target / cur
        m.postScale(f, f, fx, fy)
        clampImage()
        invalidate()
    }

    /** Das Foto muss den Ausschnitt immer vollständig bedecken. */
    private fun clampImage() {
        val r = imageRect()
        var dx = 0f
        var dy = 0f
        if (r.left > crop.left) dx = crop.left - r.left else if (r.right < crop.right) dx = crop.right - r.right
        if (r.top > crop.top) dy = crop.top - r.top else if (r.bottom < crop.bottom) dy = crop.bottom - r.bottom
        m.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        canvas.drawBitmap(b, m, bitmapPaint)

        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, crop.top, shadePaint)
        canvas.drawRect(0f, crop.bottom, w, h, shadePaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, shadePaint)
        canvas.drawRect(crop.right, crop.top, w, crop.bottom, shadePaint)

        drawBorder(canvas, crop.left, crop.top, crop.width(), crop.height(), 1f)

        canvas.drawRect(crop, linePaint)
        val tw = crop.width() / 3f
        val th = crop.height() / 3f
        for (i in 1..2) {
            canvas.drawLine(crop.left + tw * i, crop.top, crop.left + tw * i, crop.bottom, gridPaint)
            canvas.drawLine(crop.left, crop.top + th * i, crop.right, crop.top + th * i, gridPaint)
        }
        if (aspect <= 0f) {
            val hs = 7f * dens
            for (x in listOf(crop.left, crop.right)) for (y in listOf(crop.top, crop.bottom)) {
                canvas.drawRect(x - hs, y - hs, x + hs, y + hs, handlePaint)
            }
        }
    }

    private fun drawBorder(c: Canvas, left: Float, top: Float, w: Float, h: Float, scale: Float) {
        if (borderFraction <= 0f) return
        val t = borderFraction * min(w, h) * scale
        fillPaint.color = borderColor
        c.drawRect(left, top, left + w, top + t, fillPaint)
        c.drawRect(left, top + h - t, left + w, top + h, fillPaint)
        c.drawRect(left, top + t, left + t, top + h - t, fillPaint)
        c.drawRect(left + w - t, top + t, left + w, top + h - t, fillPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bmp == null) return false
        return if (aspect > 0f) touchFixed(e) else touchFree(e)
    }

    private fun touchFixed(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !scaleDetector.isInProgress && e.pointerCount == 1) {
                    m.postTranslate(e.x - lastX, e.y - lastY)
                    clampImage()
                    invalidate()
                }
                if (e.pointerCount == 1) {
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(idx); lastY = e.getY(idx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun touchFree(e: MotionEvent): Boolean {
        val hit = 36f * dens
        val minSize = 48f * dens
        val img = imageRect()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                val inY = e.y > crop.top - hit && e.y < crop.bottom + hit
                val inX = e.x > crop.left - hit && e.x < crop.right + hit
                actL = inY && abs(e.x - crop.left) < hit
                actR = inY && abs(e.x - crop.right) < hit && !actL
                actT = inX && abs(e.y - crop.top) < hit
                actB = inX && abs(e.y - crop.bottom) < hit && !actT
                moveAll = !(actL || actR || actT || actB) && crop.contains(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x; lastY = e.y
                if (actL) crop.left = (crop.left + dx).coerceIn(img.left, crop.right - minSize)
                if (actR) crop.right = (crop.right + dx).coerceIn(crop.left + minSize, img.right)
                if (actT) crop.top = (crop.top + dy).coerceIn(img.top, crop.bottom - minSize)
                if (actB) crop.bottom = (crop.bottom + dy).coerceIn(crop.top + minSize, img.bottom)
                if (moveAll) {
                    val mx = dx.coerceIn(img.left - crop.left, img.right - crop.right)
                    val my = dy.coerceIn(img.top - crop.top, img.bottom - crop.bottom)
                    crop.offset(mx, my)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                actL = false; actR = false; actT = false; actB = false; moveAll = false
            }
        }
        return true
    }

    /** Erzeugt das fertige Bild (Ausschnitt in voller Auflösung, mit Rahmen). */
    fun renderCrop(): Bitmap? {
        val b = bmp ?: return null
        val inv = Matrix()
        if (!m.invert(inv)) return null
        val r = RectF(crop)
        inv.mapRect(r)

        val l = r.left.roundToInt().coerceIn(0, b.width - 1)
        val t = r.top.roundToInt().coerceIn(0, b.height - 1)
        val w = r.width().roundToInt().coerceIn(1, b.width - l)
        val h = r.height().roundToInt().coerceIn(1, b.height - t)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(b, Rect(l, t, l + w, t + h), Rect(0, 0, w, h), bitmapPaint)
        drawBorder(c, 0f, 0f, w.toFloat(), h.toFloat(), 1f)
        return out
    }
}
