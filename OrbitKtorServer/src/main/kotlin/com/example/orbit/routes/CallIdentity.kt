package com.example.orbit.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

/** F-13: id iz proverenog JWT tokena; null van authenticate bloka */
fun ApplicationCall.userIdOrNull(): String? =
    principal<JWTPrincipal>()?.subject
