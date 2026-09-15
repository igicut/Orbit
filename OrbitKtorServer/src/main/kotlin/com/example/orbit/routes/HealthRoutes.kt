package com.example.orbit.routes

import com.example.orbit.db.Events
import com.example.orbit.model.HealthStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** F-11: GET /health, proverava i bazu (503 ako ne radi) */
fun Route.healthRoutes(database: R2dbcDatabase) {

    get("/health") {
        val databaseUp = runCatching {
            suspendTransaction(database) {
                Events.selectAll().limit(1).toList()
            }
        }.isSuccess

        call.respond(
            if (databaseUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthStatus(
                status = if (databaseUp) "UP" else "DEGRADED",
                database = if (databaseUp) "UP" else "DOWN",
            ),
        )
    }
}
