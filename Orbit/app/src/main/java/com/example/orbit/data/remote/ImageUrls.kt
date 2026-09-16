package com.example.orbit.data.remote

import com.example.orbit.BuildConfig

/** Putanja koju vraca POST /images; server ne zna svoju adresu iz ugla telefona */
private const val STORED_PREFIX = "/images/"

/**
 * F-37: slika je ili putanja sa servera ili lokalni URI koji jos nije poslat.
 */
object ImageUrls {

    fun isStored(uri: String): Boolean = uri.startsWith(STORED_PREFIX)

    /** Adresa za Coil; lokalni URI ide nepromenjen */
    fun model(uri: String): String =
        if (isStored(uri)) BuildConfig.BASE_URL.trimEnd('/') + uri else uri
}
