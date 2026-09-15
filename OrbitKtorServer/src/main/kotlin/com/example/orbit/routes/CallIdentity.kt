package com.example.orbit.routes

import io.ktor.server.application.ApplicationCall

/** F-13: id pozivaoca iz zaglavlja, nije autentifikacija */
const val USER_ID_HEADER = "X-User-Id"

fun ApplicationCall.userIdOrNull(): String? =
    request.headers[USER_ID_HEADER]?.takeIf { it.isNotBlank() }
