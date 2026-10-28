package de.autoknigge.fotopdf

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Dokument-Scanner von Google (ML Kit): Kamera, Randerkennung, Zuschnitt, Entzerren.
 * Läuft auf dem Gerät über die Google-Play-Dienste; die App braucht keine Kamera-Berechtigung.
 */
object ScanHelper {

    fun start(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    activity,
                    "Scanner nicht verfügbar: ${e.message ?: "unbekannter Fehler"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /** Die gescannten Seiten als JPEG-Adressen (leer, wenn abgebrochen). */
    fun pages(result: ActivityResult): List<Uri> {
        if (result.resultCode != Activity.RESULT_OK) return emptyList()
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return emptyList()
        return scan.pages?.map { it.imageUri } ?: emptyList()
    }
}
