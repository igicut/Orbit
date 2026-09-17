package com.example.orbit.service

import com.example.orbit.db.EventEmbeddings
import com.example.orbit.db.Events
import com.example.orbit.model.Visibility
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** Dogadjaj koji ceka vektor; tekst je vec spojen */
data class EmbeddingInput(val eventId: String, val text: String)

/** F-32: pristup bazi za vektore dogadjaja */
class ExposedEmbeddingService(private val database: R2dbcDatabase) {

    suspend fun save(eventId: String, vector: List<Float>) {
        suspendTransaction(database) {
            val exists = EventEmbeddings.selectAll()
                .where { EventEmbeddings.eventId eq eventId }
                .map { it[EventEmbeddings.eventId] }
                .singleOrNull() != null

            val now = System.currentTimeMillis()
            if (exists) {
                EventEmbeddings.update({ EventEmbeddings.eventId eq eventId }) {
                    it[EventEmbeddings.vector] = vector
                    it[updatedAt] = now
                }
            } else {
                EventEmbeddings.insert {
                    it[EventEmbeddings.eventId] = eventId
                    it[EventEmbeddings.vector] = vector
                    it[updatedAt] = now
                }
            }
        }
    }

    /** Vektori trazenih dogadjaja; nedostajuci prosto nisu u mapi */
    suspend fun vectorsFor(eventIds: List<String>): Map<String, List<Float>> {
        if (eventIds.isEmpty()) return emptyMap()
        return suspendTransaction(database) {
            EventEmbeddings.selectAll()
                .where { EventEmbeddings.eventId inList eventIds }
                .map { it[EventEmbeddings.eventId] to it[EventEmbeddings.vector] }
                .toList()
                .toMap()
        }
    }

    /** Javni dogadjaji bez vektora; privatni se ne pretrazuju, pa se ne embeduju */
    suspend fun withoutEmbedding(limit: Int): List<EmbeddingInput> = suspendTransaction(database) {
        Events.leftJoin(EventEmbeddings, { Events.id }, { EventEmbeddings.eventId })
            .selectAll()
            .where { (Events.visibility eq Visibility.PUBLIC) and EventEmbeddings.eventId.isNull() }
            .limit(limit)
            .map { EmbeddingInput(it[Events.id], embeddingText(it[Events.title], it[Events.description])) }
            .toList()
    }

    suspend fun delete(eventId: String) {
        suspendTransaction(database) {
            EventEmbeddings.deleteWhere { EventEmbeddings.eventId eq eventId }
        }
    }
}

/** Isti oblik teksta pri pravljenju, izmeni i popunjavanju */
fun embeddingText(title: String, description: String): String = "$title\n$description"
