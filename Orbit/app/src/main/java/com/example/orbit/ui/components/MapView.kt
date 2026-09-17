package com.example.orbit.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

/**
 * Prigusena svetla podloga umesto sarenog podrazumevanog OSM-a.
 * Bez API kljuca: Carto Positron ga od skoro trazi (plocice dolaze sa vodenim zigom),
 * a HOT stil je zasiceniji od podrazumevanog. Ovaj ide do zuma 16, dalje se razvlaci.
 * Natpis o autorstvu cita `CopyrightOverlay` iz samog izvora.
 */
private val MUTED_BASEMAP = object : XYTileSource(
    "Esri.WorldGrayCanvas",
    0, 16, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/"),
    "Esri, HERE, Garmin, © OpenStreetMap contributors",
) {
    // Esri slaze putanju kao z/y/x, a osmdroid podrazumevano salje z/x/y
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) +
            "/" + MapTileIndex.getY(pMapTileIndex) +
            "/" + MapTileIndex.getX(pMapTileIndex)
}

@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current

    return remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "Orbit/1.0 (+https://github.com/igicut/Orbit)"
        }
        MapView(context).apply {
            setTileSource(MUTED_BASEMAP)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
        }
    }
}
