package de.autoknigge.fotopdf

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class PdfActivity : AppCompatActivity() {

    companion object {
        private const val MAX_PHOTOS = 100
    }

    private val items = mutableListOf<PhotoItem>()
    private var nextId = 1L
    private var busy = false

    private lateinit var adapter: PhotoAdapter
    private lateinit var adManager: AdManager
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnAdd: View
    private lateinit var btnCreate: View
    private lateinit var etName: EditText
    private lateinit var rgFormat: RadioGroup
    private lateinit var rgQuality: RadioGroup
    private lateinit var progress: ProgressBar
    private lateinit var cbAsk: CheckBox
    private var pendingName = ""

    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        addUris(uris)
    }

    private val saveAs = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) startExport(uri)
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        addUris(ScanHelper.pages(result))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_pdf)

        Ui.insets(findViewById(R.id.root))

        tvCount = findViewById(R.id.tvCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnAdd = findViewById(R.id.btnAdd)
        btnCreate = findViewById(R.id.btnCreate)
        etName = findViewById(R.id.etName)
        rgFormat = findViewById(R.id.rgFormat)
        rgQuality = findViewById(R.id.rgQuality)
        progress = findViewById(R.id.progress)
        cbAsk = findViewById(R.id.cbAsk)

        etName.setText("Fotos_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date()))

        adapter = PhotoAdapter(items) { updateUi() }
        val rv = findViewById<RecyclerView>(R.id.rvPhotos)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnAdd.setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnCreate.setOnClickListener { createPdf() }
        findViewById<View>(R.id.btnScan).setOnClickListener { ScanHelper.start(this, scanLauncher) }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        adManager = AdManager(this, findViewById<FrameLayout>(R.id.adContainer), null)
        Intro.run(this) { adManager.start() }

        updateUi()
        if (savedInstanceState == null) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
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
        adapter.shutdown()
        worker.shutdown()
        super.onDestroy()
    }

    // ---------- Fotos hinzufügen ----------

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("scan", false)) {
            intent.removeExtra("scan")
            ScanHelper.start(this, scanLauncher)
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) addUris(listOf(uri))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (list != null) addUris(list)
            }
        }
    }

    private fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val free = MAX_PHOTOS - items.size
        if (free <= 0) {
            Toast.makeText(this, "Mehr als $MAX_PHOTOS Fotos sind nicht möglich.", Toast.LENGTH_LONG).show()
            return
        }
        val take = uris.take(free)
        val start = items.size
        take.forEach { items.add(PhotoItem(nextId++, it)) }
        adapter.notifyItemRangeInserted(start, take.size)
        if (take.size < uris.size) {
            Toast.makeText(this, "Es passen höchstens $MAX_PHOTOS Fotos in ein PDF.", Toast.LENGTH_LONG).show()
        }
        updateUi()
    }

    private fun updateUi() {
        tvCount.text = when (items.size) {
            0 -> "Mehrere Fotos aus der Galerie"
            1 -> "1 Foto ausgewählt – weitere hinzufügen"
            else -> "${items.size} Fotos ausgewählt – weitere hinzufügen"
        }
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        btnCreate.isEnabled = items.isNotEmpty() && !busy
        btnCreate.alpha = if (btnCreate.isEnabled) 1f else 0.5f
        btnAdd.isEnabled = !busy
    }

    // ---------- PDF erstellen ----------

    private fun createPdf() {
        if (busy || items.isEmpty()) return

        val baseName = sanitize(etName.text.toString()).ifBlank {
            "Fotos_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date())
        }
        pendingName = "$baseName.pdf"
        if (cbAsk.isChecked) {
            saveAs.launch(pendingName) // Systemdialog: Ordner und Name selbst wählen
        } else {
            startExport(null)
        }
    }

    private fun startExport(target: Uri?) {
        if (busy || items.isEmpty()) return
        val fileName = pendingName
        val a4 = rgFormat.checkedRadioButtonId == R.id.rbA4

        val maxSide: Int
        val quality: Int
        when (rgQuality.checkedRadioButtonId) {
            R.id.rbHigh -> { maxSide = 2600; quality = 90 }
            R.id.rbLow -> { maxSide = 1100; quality = 65 }
            else -> { maxSide = 1700; quality = 80 }
        }

        val snapshot = items.map { it.copy() }
        busy = true
        progress.max = snapshot.size
        progress.progress = 0
        progress.visibility = View.VISIBLE
        updateUi()

        worker.execute {
            val result = try {
                Result.success(
                    PdfExporter.export(this, snapshot, a4, maxSide, quality, fileName, target) { done ->
                        runOnUiThread { progress.progress = done }
                    }
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
            runOnUiThread {
                busy = false
                progress.visibility = View.GONE
                updateUi()
                result
                    .onSuccess { showDone(it) }
                    .onFailure {
                        Toast.makeText(
                            this,
                            "PDF konnte nicht erstellt werden: ${it.message ?: "unbekannter Fehler"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun sanitize(name: String): String =
        name.trim().removeSuffix(".pdf").replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)

    private fun showDone(r: ExportResult) {
        val sites = if (r.pages == 1) "1 Seite" else "${r.pages} Seiten"
        var msg = if (r.custom) "Gespeichert:\n${r.fileName} ($sites)" else "Gespeichert in „Downloads“:\n${r.fileName} ($sites)"
        if (r.skipped > 0) msg += "\n\n${r.skipped} Foto(s) konnten nicht gelesen werden und fehlen im PDF."

        AlertDialog.Builder(this)
            .setTitle("PDF fertig")
            .setMessage(msg)
            .setPositiveButton("Teilen") { _, _ -> sharePdf(r.uri) }
            .setNeutralButton("Öffnen") { _, _ -> openPdf(r.uri) }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun sharePdf(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "PDF teilen"))
    }

    private fun openPdf(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Keine App zum Öffnen von PDFs gefunden.", Toast.LENGTH_LONG).show()
        }
    }
}
