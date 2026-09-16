package com.example.orbit.data.image

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.orbit.data.remote.OrbitApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Isti limit kao na serveru */
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

/** Tipovi koje server prima; ostalo nema svrhe slati */
private val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

/** Kamera pise .jpg, a file:// URI nema tip u ContentResolver-u */
private val EXTENSION_TYPES = mapOf(
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "webp" to "image/webp",
)

sealed interface ImageUploadResult {
    data class Uploaded(val path: String) : ImageUploadResult

    /** Slika je nepovratno neupotrebljiva, izbacuje se iz dogadjaja */
    data object Rejected : ImageUploadResult

    /** Nema mreze; dogadjaj ceka sledecu sinhronizaciju */
    data object NoConnection : ImageUploadResult
}

/**
 * F-37: salje lokalnu sliku serveru i vraca putanju za image_uris.
 * Slika ide u originalu, da EXIF rotacija ostane netaknuta.
 */
@Singleton
class ImageUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: OrbitApiService,
) {

    suspend fun upload(uri: String): ImageUploadResult {
        val parsed = uri.toUri()
        val type = contentType(parsed) ?: return ImageUploadResult.Rejected
        val bytes = readBytes(parsed) ?: return ImageUploadResult.Rejected
        if (bytes.size > MAX_IMAGE_BYTES) return ImageUploadResult.Rejected

        // Ime fajla server ignorise, ali multipart deo mora da ga ima
        val part = MultipartBody.Part.createFormData(
            "file",
            "image",
            bytes.toRequestBody(type.toMediaType()),
        )

        val response = try {
            api.uploadImage(part)
        } catch (e: IOException) {
            return ImageUploadResult.NoConnection
        } catch (e: SerializationException) {
            return ImageUploadResult.Rejected
        }

        val path = response.body()?.path
        if (!response.isSuccessful || path == null) return ImageUploadResult.Rejected
        return ImageUploadResult.Uploaded(path)
    }

    private fun contentType(uri: Uri): String? {
        val fromResolver = context.contentResolver.getType(uri)?.lowercase()
        if (fromResolver in ALLOWED_TYPES) return fromResolver

        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        return EXTENSION_TYPES[extension]
    }

    /** null kad je dozvola za URI istekla ili je fajl obrisan */
    private suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
