package com.example.orbit.data.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val GALLERY_FOLDER = "Orbit"

/**
 * F-41: cuva sliku kao PNG u galeriju (Pictures/Orbit), npr. QR za stampanje.
 * Samo Android 10+: tamo MediaStore ne trazi nikakvu dozvolu, a starije verzije traze dozvolu za skladiste.
 */
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun saveImageToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + GALLERY_FOLDER)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false

        val written = try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        } catch (e: IOException) {
            false
        }
        // Nedovrsen fajl ne sme da ostane u galeriji kao prazna slika
        if (!written) resolver.delete(uri, null, null)
        written
    }
