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

/**
 * F-11 - GET /health.
 *
 * The point of this endpoint is NOT that the HTTP server answered - GET / already
 * proves that. The point is that it touches the database, so it catches the one
 * failure that actually happens in practice: the server started fine but MySQL
 * is not running.
 *
 * Returns 200 with both UP, or 503 with database DOWN. 503 rather than 500
 * because "temporarily unavailable" is what a caller should retry on.
 */
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
