package de.autoknigge.fotopdf

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** Startmenü mit den Funktionen der App. */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val URL_HOME = "https://autoknigge.de"
        private const val URL_IMPRINT = "https://autoknigge.de/impressum"
        private const val URL_PRIVACY = "https://autoknigge.de/fotos-zu-pdf/"
    }

    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        Ui.insets(findViewById(R.id.root))

        findViewById<View>(R.id.tilePdf).setOnClickListener {
            startActivity(Intent(this, PdfActivity::class.java))
        }
        findViewById<View>(R.id.tileCrop).setOnClickListener {
            startActivity(Intent(this, CropActivity::class.java))
        }

        mapOf(
            R.id.tvAbout to URL_HOME,
            R.id.tvPrivacy to URL_PRIVACY,
            R.id.tvImprint to URL_IMPRINT
        ).forEach { (id, url) ->
            findViewById<TextView>(id).apply {
                Ui.underline(this)
                setOnClickListener { Ui.openUrl(this@MainActivity, url) }
            }
        }
        val adLink = findViewById<TextView>(R.id.tvAdSettings)
        Ui.underline(adLink)

        val parts = (packageManager.getPackageInfo(packageName, 0).versionName ?: "").split(".")
        findViewById<TextView>(R.id.tvVersion).text = parts.take(2).joinToString(".")

        adManager = AdManager(this, findViewById<FrameLayout>(R.id.adContainer), adLink)
        adManager.start()
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
        super.onDestroy()
    }
}
