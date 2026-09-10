package com.example.orbit.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/**
 * Turns @Serializable classes into JSON responses and request bodies automatically.
 *
 * ignoreUnknownKeys is important here. The Android client sends fields the server
 * has no column for - syncedToBackend is purely client-side bookkeeping. Without
 * this flag kotlinx.serialization rejects the whole request with an exception the
 * moment it sees a field it does not recognise, which is a confusing 500 to debug.
 *
 * It also means the client can add a field before the server knows about it
 * without breaking every existing endpoint.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
}
