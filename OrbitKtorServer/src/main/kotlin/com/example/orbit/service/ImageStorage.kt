package com.example.orbit.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Prefiks putanje koja se cuva u events.image_uris */
const val IMAGE_PATH_PREFIX = "/images/"

/** Najveca dozvoljena slika */
const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

/** Tip koji klijent salje -> ekstenzija pod kojom se fajl cuva */
private val ALLOWED_TYPES = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)

/** Ime je uvek UUID sa poznatom ekstenzijom; sve ostalo odbijamo */
private val STORED_NAME = Regex("^[0-9a-f-]{36}\\.(jpg|png|webp)$")

/** Bajtovi se upisuju u delovima, cela slika ne ulazi u memoriju */
private const val COPY_BUFFER_BYTES = 8 * 1024

sealed interface SaveOutcome {
    data class Saved(val path: String) : SaveOutcome
    data object UnsupportedType : SaveOutcome
    data object TooLarge : SaveOutcome
}

/** Folder sa slikama, relativan na radni folder servera */
private const val DEFAULT_UPLOAD_DIR = "uploads"

/**
 * F-37: slike dogadjaja na disku servera.
 * Ime fajla pravi server, ime iz zahteva se ne koristi.
 */
class ImageStorage(directory: String) {

    private val root = File(directory).absoluteFile

    init {
        root.mkdirs()
    }

    /** Nema stanja osim foldera, pa sme vise instanci nad istim folderom */
    companion object {
        fun fromEnvironment(): ImageStorage = ImageStorage(
            System.getenv("ORBIT_UPLOAD_DIR")?.takeIf { it.isNotBlank() } ?: DEFAULT_UPLOAD_DIR,
        )
    }

    suspend fun save(contentType: String?, source: InputStream): SaveOutcome {
        val extension = ALLOWED_TYPES[contentType?.substringBefore(';')?.trim()?.lowercase()]
            ?: return SaveOutcome.UnsupportedType

        val file = File(root, UUID.randomUUID().toString() + "." + extension)
        // Upis u fajl blokira, zato van niti koja opsluzuje zahteve
        val written = withContext(Dispatchers.IO) {
            try {
                source.use { input -> copyLimited(input, file) }
            } catch (e: IOException) {
                file.delete()
                throw e
            }
        }

        if (written == null) {
            file.delete()
            return SaveOutcome.TooLarge
        }
        return SaveOutcome.Saved(IMAGE_PATH_PREFIX + file.name)
    }

    /** null kad predje limit; nedovrsen fajl brise pozivalac */
    private fun copyLimited(source: InputStream, target: File): Long? {
        var total = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        target.outputStream().use { output ->
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMAGE_BYTES) return null
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    /** null za nepoznato ime ili ime koje pokusava da izadje iz foldera */
    fun find(name: String): File? {
        if (!STORED_NAME.matches(name)) return null
        val file = File(root, name)
        return if (file.isFile) file else null
    }

    /** Brise fajlove obrisanog dogadjaja; strane putanje preskace */
    fun deleteAll(paths: List<String>) {
        paths.forEach { path ->
            if (isStoredPath(path)) find(path.removePrefix(IMAGE_PATH_PREFIX))?.delete()
        }
    }
}

/** Dogadjaj sme da nosi samo slike sa ovog servera */
fun isStoredPath(path: String): Boolean =
    path.startsWith(IMAGE_PATH_PREFIX) && STORED_NAME.matches(path.removePrefix(IMAGE_PATH_PREFIX))
