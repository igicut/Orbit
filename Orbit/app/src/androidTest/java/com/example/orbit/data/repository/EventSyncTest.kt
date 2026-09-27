package com.example.orbit.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.orbit.data.image.ImageUploader
import com.example.orbit.data.local.AppDatabase
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.local.toDomain
import com.example.orbit.data.notification.EventNotifier
import com.example.orbit.data.remote.OrbitApiService
import com.example.orbit.data.remote.dto.EventDto
import com.example.orbit.di.NetworkModule
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val OWNER = "sync-test-owner"
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * F-15 i F-37: slanje dogadjaja napravljenog bez mreze i neuspesno otpremanje fotografije.
 * Room, Retrofit i ImageUploader su pravi; zamenjen je samo server (MockWebServer),
 * da bi test mogao da ugasi mrezu i da bira odgovore.
 * Ne pokrece sesiju i ne pise token, pa ne dira nalog prijavljen na telefonu.
 * Test odbijenog dogadjaja prikazuje pravo obavestenje "Sync test" na telefonu; to je ocekivano.
 */
@RunWith(AndroidJUnit4::class)
class EventSyncTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val json: Json = NetworkModule.provideJson()

    private lateinit var server: MockWebServer
    private var port = 0
    private lateinit var database: AppDatabase
    private lateinit var repository: EventRepositoryImpl
    private val photos = mutableListOf<File>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        port = server.port

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val api = Retrofit.Builder()
            .baseUrl("http://localhost:$port/")
            // Kratki timeout-i: test bez mreze ne treba da ceka
            .client(OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OrbitApiService::class.java)

        repository = EventRepositoryImpl(
            eventDao = database.eventDao(),
            currentUser = CurrentUser(context, EventNotifier(context)),
            registrationDao = database.registrationDao(),
            attendanceDao = database.attendanceDao(),
            ratingDao = database.ratingDao(),
            userDao = database.userDao(),
            blockedUserDao = database.blockedUserDao(),
            api = api,
            imageUploader = ImageUploader(context, api),
            notifier = EventNotifier(context),
        )
    }

    @After
    fun tearDown() {
        server.close()
        database.close()
        photos.forEach { it.delete() }
    }

    @Test
    fun eventCreatedOfflineStaysPendingAndIsSentWhenOnline() = runBlocking {
        val event = newEvent(photos = listOf(photo()), visibility = Visibility.PRIVATE)
        repository.saveEvent(event)

        // Bez mreze: na portu vise niko ne slusa
        server.close()
        assertEquals(PushResult.Pending, repository.pushEvent(event))

        val offline = database.eventDao().getById(event.id)!!.toDomain()
        assertFalse("still waiting for the server", offline.syncedToBackend)
        assertEquals("the local photo is kept for the next try", event.imageUris, offline.imageUris)
        assertEquals(listOf(event.id), database.eventDao().getPendingUploads(OWNER).map { it.id })

        // Mreza se vraca: isti port, novi server
        server = MockWebServer()
        server.start(port)
        val uploaded = "/images/${UUID.randomUUID()}.jpg"
        server.enqueue(jsonResponse(201, """{"path":"$uploaded"}"""))
        server.enqueue(jsonResponse(201, serverCopy(event, listOf(uploaded), accessCode = "K7QW2M")))

        val synced = repository.pushEvent(event)

        assertTrue(synced is PushResult.Sent)
        val upload = server.takeRequest(5, TimeUnit.SECONDS)!!
        val create = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST /images", "${upload.method} ${upload.target}")
        assertEquals("POST /events", "${create.method} ${create.target}")
        val sentBody = create.body!!.utf8()
        assertTrue("the event carries the server path", sentBody.contains(uploaded))
        assertFalse("no local file path reaches the server", sentBody.contains("file://"))

        val stored = database.eventDao().getById(event.id)!!.toDomain()
        assertTrue(stored.syncedToBackend)
        assertEquals(listOf(uploaded), stored.imageUris)
        assertEquals("the access code comes from the server", "K7QW2M", stored.accessCode)
        assertTrue(database.eventDao().getPendingUploads(OWNER).isEmpty())
    }

    @Test
    fun lostReplyIsTreatedAsAlreadySent() = runBlocking {
        val event = newEvent(photos = listOf(photo()), visibility = Visibility.PRIVATE)
        repository.saveEvent(event)
        val uploaded = "/images/${UUID.randomUUID()}.jpg"

        // Raniji pokusaj je stigao do servera, ali se odgovor izgubio: server sada kaze 409
        server.enqueue(jsonResponse(201, """{"path":"$uploaded"}"""))
        server.enqueue(jsonResponse(409, "An event with this id already exists"))
        server.enqueue(jsonResponse(200, serverCopy(event, listOf(uploaded), accessCode = "P3XN8R")))

        val synced = repository.pushEvent(event)

        assertTrue(synced is PushResult.Sent)
        server.takeRequest(5, TimeUnit.SECONDS)
        server.takeRequest(5, TimeUnit.SECONDS)
        val refresh = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET /events/${event.id}", "${refresh.method} ${refresh.target}")
        val stored = database.eventDao().getById(event.id)!!.toDomain()
        assertTrue(stored.syncedToBackend)
        assertEquals("the server copy brings the access code", "P3XN8R", stored.accessCode)
    }

    @Test
    fun rejectedPhotoIsLeftOutAndTheEventGoesWithTheOthers() = runBlocking {
        val rejected = photo()
        val accepted = photo()
        val event = newEvent(photos = listOf(rejected, accepted))
        repository.saveEvent(event)
        val uploaded = "/images/${UUID.randomUUID()}.jpg"

        server.enqueue(jsonResponse(415, "Only JPEG, PNG and WEBP images are accepted"))
        server.enqueue(jsonResponse(201, """{"path":"$uploaded"}"""))
        server.enqueue(jsonResponse(201, serverCopy(event, listOf(uploaded), accessCode = null)))

        val synced = repository.pushEvent(event)

        assertTrue(synced is PushResult.Sent)
        server.takeRequest(5, TimeUnit.SECONDS)
        server.takeRequest(5, TimeUnit.SECONDS)
        val create = server.takeRequest(5, TimeUnit.SECONDS)!!
        val sentBody = create.body!!.utf8()
        assertTrue(sentBody.contains(uploaded))
        assertFalse("the rejected photo is not sent", sentBody.contains(rejected))
        assertEquals(listOf(uploaded), database.eventDao().getById(event.id)!!.toDomain().imageUris)
    }

    @Test
    fun eventTheServerRejectsIsRemovedInsteadOfWaitingForever() = runBlocking {
        val event = newEvent(photos = listOf(photo()))
        repository.saveEvent(event)
        val uploaded = "/images/${UUID.randomUUID()}.jpg"

        // Npr. telefon je bio van mreze dok je pocetak prosao; server ovo nikad nece primiti
        server.enqueue(jsonResponse(201, """{"path":"$uploaded"}"""))
        server.enqueue(jsonResponse(400, "Pocetak mora biti u buducnosti"))

        val result = repository.pushEvent(event)

        assertEquals(PushResult.Rejected("Pocetak mora biti u buducnosti"), result)
        assertNull("the local copy is removed", database.eventDao().getById(event.id))
        assertTrue("nothing is left to retry", database.eventDao().getPendingUploads(OWNER).isEmpty())
    }

    /** Mala lokalna "fotografija"; ImageUploader tip odredjuje po ekstenziji */
    private fun photo(): String {
        val file = File(context.cacheDir, "sync-test-${UUID.randomUUID()}.jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        photos += file
        return Uri.fromFile(file).toString()
    }

    private fun newEvent(photos: List<String>, visibility: Visibility = Visibility.PUBLIC) = Event(
        id = UUID.randomUUID().toString(),
        ownerId = OWNER,
        title = "Sync test",
        description = "Created without a connection",
        latitude = 44.8,
        longitude = 20.46,
        startTime = System.currentTimeMillis() + 3 * DAY_MS,
        category = EventCategory.OTHER,
        visibility = visibility,
        imageUris = photos,
    )

    /** Ono sto bi pravi server vratio: sinhronizovan dogadjaj sa njegovim putanjama i kodom */
    private fun serverCopy(event: Event, imageUris: List<String>, accessCode: String?): String =
        json.encodeToString(
            EventDto.serializer(),
            EventDto(
                id = event.id,
                ownerId = event.ownerId,
                title = event.title,
                description = event.description,
                latitude = event.latitude,
                longitude = event.longitude,
                startTime = event.startTime,
                category = event.category,
                visibility = event.visibility,
                imageUris = imageUris,
                accessCode = accessCode,
                createdAt = event.createdAt,
            ),
        )

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
