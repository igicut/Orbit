package com.example.orbit.data.repository

import com.example.orbit.domain.model.Event
import kotlinx.coroutines.flow.Flow


interface EventRepository {

    fun observeEvents(): Flow<List<Event>>

    fun observeEventsByOwner(ownerId: String): Flow<List<Event>>

    fun observeEvent(id: String): Flow<Event?>

    suspend fun getEvent(id: String): Event?

    suspend fun saveEvent(event: Event)

    suspend fun deleteEvent(id: String)

    suspend fun syncPublicEvents(latitude: Double, longitude: Double, radiusKm: Double)
    suspend fun pushEvent(event: Event)
}
