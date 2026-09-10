package com.example.orbit.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText

/**
 * Catches anything thrown inside a route so the server returns a response
 * instead of dropping the connection.
 *
 * NOTE: this currently sends the exception text straight to the client, which is
 * useful while developing and wrong in production - it leaks internals such as
 * table and column names. Replace with a generic message before any real deployment.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "500: $cause",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}
