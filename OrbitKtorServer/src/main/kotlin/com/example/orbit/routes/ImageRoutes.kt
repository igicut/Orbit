package com.example.orbit.routes

import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedImageService
import com.example.orbit.service.ExposedRatingService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.IMAGE_PATH_PREFIX
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
fun Route.imageRoutes(
    storage: ImageStorage,
    imageService: ExposedImageService,
    eventService: ExposedEventService,
    ratingService: ExposedRatingService,
    userDataService: ExposedUserDataService,
) {

    /**
     * Sliku vidi onaj ko ju je otpremio, i svako ko sme da vidi dogadjaj
     * na kome se nalazi, bilo kao slika dogadjaja ili uz utisak.
     */
    suspend fun canView(path: String, userId: String): Boolean {
        if (imageService.uploaderOf(path) == userId) return true

        for (event in eventService.findByImage(path)) {
            if (userDataService.canAccess(event, userId)) return true
        }
        for (eventId in ratingService.eventIdsWithImage(path)) {
            val event = eventService.findById(eventId) ?: continue
            if (userDataService.canAccess(event, userId)) return true
        }
        return false
    }

    /** Multipart sa jednim fajlom; vraca putanju za image_uris */
    post("/images") {
        val userId = call.userIdOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Not logged in")

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
            is SaveOutcome.Saved -> {
                // Po ovom redu dogadjaj i utisak kasnije primaju samo sopstvene slike
                imageService.recordUpload(outcome.path, userId)
                call.respond(HttpStatusCode.Created, ImageUploadResponse(outcome.path))
            }
            SaveOutcome.UnsupportedType ->
                call.respond(HttpStatusCode.UnsupportedMediaType, "Only JPEG, PNG and WEBP images are accepted")
            SaveOutcome.TooLarge ->
                call.respond(HttpStatusCode.PayloadTooLarge, "An image can be at most ${MAX_IMAGE_BYTES / 1024 / 1024} MB")
            SaveOutcome.Empty -> call.respond(HttpStatusCode.BadRequest, "Slika je prazna")
            null -> call.respond(HttpStatusCode.BadRequest, "The request has no file part")
        }
    }

    /** Nepoznato ime, neispravno ime i slika bez prava pristupa izgledaju isto: 404 */
    get("/images/{name}") {
        val userId = call.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val name = call.parameters["name"]
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val file = storage.find(name)
            ?: return@get call.respond(HttpStatusCode.NotFound)

        if (!canView(IMAGE_PATH_PREFIX + name, userId)) {
            return@get call.respond(HttpStatusCode.NotFound)
        }

        // Private: sme samo kes na telefonu, ne i usput, jer slika moze biti sa privatnog dogadjaja
        call.response.cacheControl(CacheControl.MaxAge(CACHE_SECONDS, visibility = CacheControl.Visibility.Private))
        call.respondFile(file)
    }
}
