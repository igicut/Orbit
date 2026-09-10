package com.example.orbit.routes

import io.ktor.server.application.ApplicationCall

/**
 * F-13 - "who is calling?".
 *
 * There is no login. The client generates an id on first launch and sends it in
 * this header on every request. Anything that needs an owner reads it from here.
 *
 * This is NOT authentication - a caller can send any id they like. It is enough
 * for a course project, and the documentation says so explicitly. Do not describe
 * it as security in the thesis.
 */
const val USER_ID_HEADER = "X-User-Id"

fun ApplicationCall.userIdOrNull(): String? =
    request.headers[USER_ID_HEADER]?.takeIf { it.isNotBlank() }
