package com.example.orbit.routes

import com.example.orbit.service.ImageStorage
import com.example.orbit.service.MAX_IMAGE_BYTES
import com.example.orbit.service.SaveOutcome
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable

/** Sadrzaj se ne menja jer je ime nasumicno, pa sme dugo u kesu */
private const val CACHE_SECONDS = 30 * 24 * 60 * 60

@Serializable
data class ImageUploadResponse(val path: String)

/** F-37: prijem i izdavanje slika dogadjaja */
fun Route.imageRoutes(storage: ImageStorage) {

    /** Multipart sa jednim fajlom; vraca putanju za image_uris */
    post("/images") {
        // Bez provere receiveMultipart baca izuzetak, a klijent bi dobio 500
        if (!call.request.contentType().match(ContentType.MultiPart.FormData)) {
            return@post call.respond(
                HttpStatusCode.UnsupportedMediaType,
                "Send the image as multipart/form-data",
            )
        }

        val multipart = call.receiveMultipart()
        var outcome: SaveOutcome? = null

        while (true) {
            val part = multipart.readPart() ?: break
            try {
                // Prvi fajl u zahtevu je slika, ostale delove preskacemo
                if (part is PartData.FileItem && outcome == null) {
                    outcome = storage.save(part.contentType?.toString(), part.provider().toInputStream())
                }
            } finally {
                part.release()
            }
        }

        when (outcome) {
            is SaveOutcome.Saved -> call.respond(HttpStatusCode.Created, ImageUploadResponse(outcome.path))
            SaveOutcome.UnsupportedType ->
                call.respond(HttpStatusCode.UnsupportedMediaType, "Only JPEG, PNG and WEBP images are accepted")
            SaveOutcome.TooLarge ->
                call.respond(HttpStatusCode.PayloadTooLarge, "An image can be at most ${MAX_IMAGE_BYTES / 1024 / 1024} MB")
            null -> call.respond(HttpStatusCode.BadRequest, "The request has no file part")
        }
    }

    /** Nepoznato ili neispravno ime izgleda isto kao nepostojeca slika */
    get("/images/{name}") {
        val file = call.parameters["name"]?.let { storage.find(it) }
            ?: return@get call.respond(HttpStatusCode.NotFound)

        call.response.cacheControl(CacheControl.MaxAge(CACHE_SECONDS))
        call.respondFile(file)
    }
}
