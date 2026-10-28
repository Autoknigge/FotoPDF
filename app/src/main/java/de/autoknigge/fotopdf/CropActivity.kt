package de.autoknigge.fotopdf

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import java.io.BufferedOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CropActivity : AppCompatActivity() {

    private class Fmt(val label: String, val ratio: Float) // 0 = frei, -1 = Original

    private val formats = listOf(
        Fmt("Frei", 0f),
        Fmt("Original", -1f),
        Fmt("1:1", 1f),
        Fmt("4:3", 4f / 3f),
        Fmt("3:2 (10×15)", 1.5f),
        Fmt("4:5 (Instagram)", 0.8f),
        Fmt("16:9", 16f / 9f),
        Fmt("9:16 (Status)", 9f / 16f),
        Fmt("Passbild 35×45", 35f / 45f),
        Fmt("A4", 297f / 210f)
    )

    private class Frame(val label: String, val fraction: Float)

    private val frames = listOf(
        Frame("Kein", 0f), Frame("Dünn", 0.02f), Frame("Mittel", 0.05f), Frame("Breit", 0.09f)
    )

    private val colors = listOf(
        0xFFFFFFFF.toInt(), 0xFFF3EFE0.toInt(), 0xFF111111.toInt(), 0xFFD4AF37.toInt(),
        0xFF1F4D3A.toInt(), 0xFF6B4A2F.toInt(), 0xFFA63D2F.toInt()
    )

    private lateinit var cropView: CropView
    private lateinit var adManager: AdManager
    private val worker = Executors.newSingleThreadExecutor()

    private var photo: Bitmap? = null
    private var fmtIndex = 2 // 1:1
    private var curAspect = 1f
    private var frameIndex = 0
    private var colorIndex = 0
    private var busy = false

    private val formatViews = ArrayList<TextView>()
    private val frameViews = ArrayList<TextView>()
    private val colorViews = ArrayList<View>()

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(uri) else if (photo == null) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_crop)
        Ui.insets(findViewById(R.id.root))

        cropView = findViewById(R.id.cropView)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnNew).setOnClickListener { pick() }
        findViewById<View>(R.id.btnRotate).setOnClickListener { rotate() }
        findViewById<View>(R.id.btnOrient).setOnClickListener { swapOrientation() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        buildFormatChips()
        buildFrameChips()
        buildColorDots()
        applyBorder()

        adManager = AdManager(this, findViewById<FrameLayout>(R.id.adContainer), null)
        adManager.start()

        val incoming = if (intent?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        } else null
        if (incoming != null) loadPhoto(incoming) else if (savedInstanceState == null) pick()
    }

    override fun onPause() {
        adManager.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adManager.resume()
    }

    override fun onDestroy() {
        adManager.destroy()
        worker.shutdown()
        super.onDestroy()
    }

    private fun pick() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- Foto laden ----------

    private fun loadPhoto(uri: Uri) {
        worker.execute {
            val bmp = try {
                Photos.loadOriented(this, uri, 3000)
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            runOnUiThread {
                if (bmp == null) {
                    Toast.makeText(this, "Das Foto konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
                    if (photo == null) finish()
                } else {
                    setPhoto(bmp)
                }
            }
        }
    }

    private fun setPhoto(bmp: Bitmap) {
        val old = photo
        photo = bmp
        cropView.setBitmap(bmp)
        selectFormat(fmtIndex)
        if (old != null && old !== bmp) old.recycle()
    }

    // ---------- Formate ----------

    private fun chip(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) }
        isClickable = true
    }

    private fun styleChip(tv: TextView, selected: Boolean) {
        tv.setBackgroundResource(if (selected) R.drawable.bg_chip_sel else R.drawable.bg_chip)
        tv.setTextColor(if (selected) 0xFF2E2108.toInt() else 0xFFF3EFE0.toInt())
    }

    private fun buildFormatChips() {
        val box = findViewById<LinearLayout>(R.id.llFormats)
        formats.forEachIndexed { i, f ->
            val c = chip(f.label)
            c.setOnClickListener { selectFormat(i) }
            box.addView(c)
            formatViews.add(c)
        }
        formatViews.forEachIndexed { i, v -> styleChip(v, i == fmtIndex) }
    }

    private fun selectFormat(i: Int) {
        fmtIndex = i
        formatViews.forEachIndexed { idx, v -> styleChip(v, idx == i) }
        val ratio = formats[i].ratio
        curAspect = when {
            ratio < 0f -> photo?.let { it.width.toFloat() / it.height } ?: 1f
            else -> ratio
        }
        findViewById<View>(R.id.btnOrient).alpha = if (curAspect > 0f) 1f else 0.4f
        cropView.setAspect(curAspect)
    }

    private fun swapOrientation() {
        if (curAspect <= 0f) return
        curAspect = 1f / curAspect
        cropView.setAspect(curAspect)
    }

    private fun rotate() {
        val b = photo ?: return
        try {
            val rotated = Bitmap.createBitmap(
                b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f) }, true
            )
            setPhoto(rotated)
        } catch (e: OutOfMemoryError) {
            Toast.makeText(this, "Zu wenig Speicher zum Drehen.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Rahmen ----------

    private fun buildFrameChips() {
        val box = findViewById<LinearLayout>(R.id.llFrames)
        frames.forEachIndexed { i, f ->
            val c = chip(f.label)
            c.setOnClickListener {
                frameIndex = i
                frameViews.forEachIndexed { idx, v -> styleChip(v, idx == i) }
                applyBorder()
            }
            box.addView(c)
            frameViews.add(c)
        }
        frameViews.forEachIndexed { i, v -> styleChip(v, i == frameIndex) }
    }

    private fun dotBackground(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(if (selected) dp(3) else dp(1), if (selected) 0xFFD9B954.toInt() else 0x88FFFFFF.toInt())
    }

    private fun buildColorDots() {
        val box = findViewById<LinearLayout>(R.id.llColors)
        colors.forEachIndexed { i, col ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
            v.background = dotBackground(col, i == colorIndex)
            v.setOnClickListener {
                colorIndex = i
                colorViews.forEachIndexed { idx, dot -> dot.background = dotBackground(colors[idx], idx == i) }
                applyBorder()
            }
            box.addView(v)
            colorViews.add(v)
        }
    }

    private fun applyBorder() {
        cropView.setBorder(frames[frameIndex].fraction, colors[colorIndex])
    }

    // ---------- Speichern ----------

    private fun save() {
        if (busy || photo == null) return
        val out = cropView.renderCrop() ?: return
        busy = true
        Toast.makeText(this, "Speichere …", Toast.LENGTH_SHORT).show()
        worker.execute {
            val result = try {
                Result.success(writeJpeg(out))
            } catch (t: Throwable) {
                Result.failure(t)
            }
            out.recycle()
            runOnUiThread {
                busy = false
                result
                    .onSuccess { showDone(it) }
                    .onFailure {
                        Toast.makeText(
                            this, "Speichern fehlgeschlagen: ${it.message ?: "unbekannter Fehler"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun writeJpeg(bmp: Bitmap): Uri {
        val name = "Foto_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Fotos zu PDF")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Datei konnte nicht angelegt werden.")
        try {
            val stream = resolver.openOutputStream(target)
                ?: throw IllegalStateException("Datei konnte nicht geschrieben werden.")
            BufferedOutputStream(stream, 64 * 1024).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return target
        } catch (t: Throwable) {
            try {
                resolver.delete(target, null, null)
            } catch (ignored: Exception) {
            }
            throw t
        }
    }

    private fun showDone(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Foto gespeichert")
            .setMessage("Du findest es in „Bilder“ im Ordner „Fotos zu PDF“.")
            .setPositiveButton("Teilen") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Foto teilen"))
            }
            .setNeutralButton("In PDF") { _, _ ->
                val toPdf = Intent(this, PdfActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(toPdf)
            }
            .setNegativeButton("Schließen", null)
            .show()
    }
}
