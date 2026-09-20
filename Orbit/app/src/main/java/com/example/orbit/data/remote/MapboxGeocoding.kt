package com.example.orbit.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL

/** Jedan pogodak pretrage adresa */
data class AddressHit(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

private const val SEARCH_URL = "https://api.mapbox.com/search/geocode/v6/forward"
private const val RESULT_LIMIT = 5

/**
 * F-17: trazi adresu preko Mapbox Geocoding v6.
 * Prazna lista znaci i "nema pogodaka" i "mreza ne radi"; ekran u oba slucaja kaze isto.
 */
suspend fun searchAddresses(
    query: String,
    accessToken: String,
    /** Centar mape, da blizi rezultati budu prvi; null ako se jos ne zna */
    nearLatitude: Double? = null,
    nearLongitude: Double? = null,
): List<AddressHit> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()

    val url = Uri.parse(SEARCH_URL).buildUpon()
        .appendQueryParameter("q", query)
        .appendQueryParameter("limit", RESULT_LIMIT.toString())
        .appendQueryParameter("access_token", accessToken)
        .apply {
            if (nearLatitude != null && nearLongitude != null) {
                appendQueryParameter("proximity", "$nearLongitude,$nearLatitude")
            }
        }
        .build()

    val body = try {
        URL(url.toString()).readText()
    } catch (e: IOException) {
        return@withContext emptyList()
    }

    try {
        readHits(body)
    } catch (e: JSONException) {
        emptyList()
    }
}

/** Odgovor: features[].properties.full_address i geometry.coordinates kao [lng, lat] */
private fun readHits(body: String): List<AddressHit> {
    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()

    return (0 until features.length()).mapNotNull { index ->
        val feature = features.getJSONObject(index)
        val properties = feature.optJSONObject("properties") ?: return@mapNotNull null
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            ?: return@mapNotNull null

        val name = properties.optString("full_address").ifBlank { properties.optString("name") }
        if (name.isBlank() || coordinates.length() < 2) return@mapNotNull null

        AddressHit(
            name = name,
            latitude = coordinates.getDouble(1),
            longitude = coordinates.getDouble(0),
        )
    }
}
