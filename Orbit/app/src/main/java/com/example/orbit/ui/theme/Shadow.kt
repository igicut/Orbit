package com.example.orbit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Senka u boji palete umesto podrazumevane crne. Crna na kremu izgleda kao siva mrlja
 * i razbija topao ton, pa se boja uzima iz teme i prati svetlu i tamnu varijantu.
 */
fun Modifier.warmShadow(elevation: Dp, shape: Shape): Modifier = composed {
    val color = MaterialTheme.orbitAccents.shadow
    shadow(elevation = elevation, shape = shape, ambientColor = color, spotColor = color)
}
