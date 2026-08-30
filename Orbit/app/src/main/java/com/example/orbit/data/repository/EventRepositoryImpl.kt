package com.example.orbit.data.repository

import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.local.toDomain
import com.example.orbit.data.local.toEntity
import com.example.orbit.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
) : EventRepository {

    override fun observeEvents(): Flow<List<Event>> =
        eventDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeEventsByOwner(ownerId: String): Flow<List<Event>> =
        eventDao.observeByOwner(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeEvent(id: String): Flow<Event?> =
        eventDao.observeById(id).map { row -> row?.toDomain() }

    override suspend fun getEvent(id: String): Event? = eventDao.getById(id)?.toDomain()

    override suspend fun saveEvent(event: Event) = eventDao.upsert(event.toEntity())

    override suspend fun deleteEvent(id: String) = eventDao.deleteById(id)

    override suspend fun syncPublicEvents(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ) {
        // TODO: with ktor
    }

    override suspend fun pushEvent(event: Event) {
        // TODO: POST to the backend, and resave.
    }
}
