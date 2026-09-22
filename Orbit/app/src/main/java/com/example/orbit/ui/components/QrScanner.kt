package com.example.orbit.ui.components

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private const val TAG = "QrScanner"

/**
 * F-41: otvara Google-ov ekran za skeniranje i vraca tekst iz QR-a.
 * Kameru, dozvolu za nju i sam ekran daje Play services, pa aplikacija nema svoj kod za kameru.
 * Kad gost odustane (nazad), ne zove se nista.
 */
fun scanQrCode(context: Context, onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
    // Samo QR, da barkod sa proizvoda ne zavrsi kao pokusaj potvrde dolaska
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) }
        .addOnFailureListener { error ->
            // Najcesce se modul skenera jos skida; razlog ostaje u Logcat-u za proveru
            Log.w(TAG, "QR scanner unavailable", error)
            onUnavailable()
        }
}
