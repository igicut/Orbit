package com.example.orbit.data.local

import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.local.entity.EventEntity
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility
import java.util.UUID

/** NEKORISCENO: stari seed, podaci sada stizu sa servera */

private const val SEED_OWNER_ID = "00000000-0000-0000-0000-00000000feed"

private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

suspend fun seedDatabase(eventDao: EventDao) {
    val now = System.currentTimeMillis()

    val events = listOf(
        EventEntity(
            id = UUID.randomUUID().toString(),
            ownerId = SEED_OWNER_ID,
            title = "Kviz veče u centru",
            description = "Opšte znanje u ekipama do 5 ljudi. Prijave na licu mesta, " +
                "početak u 20h. Pobednička ekipa dobija piće za sto.",
            latitude = 44.8125449,
            longitude = 20.46123,
            startTime = now + 2 * ONE_DAY_MS,
            category = EventCategory.SOCIAL,
            visibility = Visibility.PUBLIC,
            imageUris = emptyList(),
            address = "Beograd, centar",
            durationMinutes = 180,
            capacity = 40,
            price = null,
            requiresReservation = false,
            accessCode = null,
            avgRating = 4.5f,
            ratingCount = 12,
            createdAt = now,
            syncedToBackend = false,
        ),
        EventEntity(
            id = UUID.randomUUID().toString(),
            ownerId = SEED_OWNER_ID,
            title = "Hakaton na ETF-u",
            description = "Dvadesetčetvoročasovni hakaton otvoren za sve studente. " +
                "Timovi do 4 člana, teme se objavljuju na početku.",
            latitude = 44.9055056,
            longitude = 20.4751359,
            startTime = now + 5 * ONE_DAY_MS,
            category = EventCategory.TECH,
            visibility = Visibility.PUBLIC,
            imageUris = emptyList(),
            address = "Elektrotehnički fakultet, Beograd",
            durationMinutes = 1440,
            capacity = 120,
            price = 500.0,
            requiresReservation = true,
            accessCode = null,
            avgRating = 4.8f,
            ratingCount = 31,
            createdAt = now,
            syncedToBackend = false,
        ),
    )

    events.forEach { eventDao.upsert(it) }
}
