package com.example.orbit.ui.components

import android.content.Context
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private const val TAG = "QrScanner"

/** Samo QR, da barkod sa proizvoda ne zavrsi kao pokusaj potvrde dolaska */
private val qrOnly = GmsBarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
    .build()

/**
 * F-41: otvara Google-ov ekran za skeniranje i vraca tekst iz QR-a.
 * Kameru, dozvolu za nju i sam ekran daje Play services, pa aplikacija nema svoj kod za kameru.
 * Kad gost odustane (nazad), ne zove se nista.
 */
fun scanQrCode(context: Context, onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
    val scanner = GmsBarcodeScanning.getClient(context, qrOnly)

    scanner.startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) }
        .addOnFailureListener { error ->
            // Skener je zaseban modul Play services-a i moze da nedostaje (npr. posle reinstalacije
            // aplikacije). Zato se odmah trazi i preuzimanje, da sledeci pokusaj prodje.
            Log.w(TAG, "QR scanner unavailable", error)
            requestScannerModule(context)
            onUnavailable()
        }
}

/**
 * F-41: trazi od Play services-a da skine modul skenera. Zove se pri pokretanju aplikacije,
 * da modul bude na telefonu pre nego sto gost stane na ulaz, i posle neuspelog skeniranja.
 * Ishod se samo loguje; modul koji vec postoji ne skida se ponovo.
 */
fun requestScannerModule(context: Context) {
    val request = ModuleInstallRequest.newBuilder()
        .addApi(GmsBarcodeScanning.getClient(context, qrOnly))
        .build()

    ModuleInstall.getClient(context)
        .installModules(request)
        .addOnSuccessListener { Log.i(TAG, "Scanner module requested") }
        .addOnFailureListener { error -> Log.w(TAG, "Scanner module download failed", error) }
}
