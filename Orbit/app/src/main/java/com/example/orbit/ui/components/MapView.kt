package com.example.orbit.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.LayerDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.orbit.R
import com.mapbox.geojson.Point

/** Prigusena podloga napravljena u Mapbox Studio-u; jedino mesto gde se stil ucitava */
const val ORBIT_MAP_STYLE = "mapbox://styles/igorcutovic/cmu79ouws003101sfeevq944c"
// const val ORBIT_MAP_STYLE = "mapbox://styles/igorcutovic/cmu753pfl001h01r112rrf1jk"

/** Pocetni zum, isti kao ranije */
const val DEFAULT_MAP_ZOOM = 13.0

/** Centar Beograda, kad nema ni pozicije ni dogadjaja */
val DEFAULT_MAP_CENTRE: Point = Point.fromLngLat(20.4612, 44.8125)

/** Mapbox ocekuje (lng, lat), a domen cuva (lat, lng); jedina tacka pretvaranja */
fun pointOf(latitude: Double, longitude: Double): Point = Point.fromLngLat(longitude, latitude)

/**
 * Pin kao bitmapa: kremast obrub ispod obojenog tela, da se susedni pinovi ne sliju.
 * Isti crtezi kao ranije, samo Mapbox trazi bitmapu umesto Drawable-a.
 */
fun Context.pinBitmap(color: Color): Bitmap? {
    val outline = ContextCompat.getDrawable(this, R.drawable.ic_map_pin_outline) ?: return null
    // mutate() da dve boje pina ne dele isti tint
    val body = ContextCompat.getDrawable(this, R.drawable.ic_map_pin)?.mutate() ?: return null
    body.setTint(color.toArgb())

    // Oba sloja su istog okvira, pa se poklapaju bez pomeranja
    val layers = LayerDrawable(arrayOf(outline, body))
    val width = layers.intrinsicWidth
    val height = layers.intrinsicHeight
    if (width <= 0 || height <= 0) return null

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    layers.setBounds(0, 0, width, height)
    layers.draw(Canvas(bitmap))
    return bitmap
}

/** Bitmapa se pravi jednom po boji, ne na svakoj rekompoziciji */
@Composable
fun rememberPinBitmap(color: Color): Bitmap? {
    val context = LocalContext.current
    return remember(color) { context.pinBitmap(color) }
}
