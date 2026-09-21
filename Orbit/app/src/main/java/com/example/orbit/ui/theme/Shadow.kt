package com.example.orbit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Senka u boji palete umesto podrazumevane crne. Crna na kremu izgleda kao siva mrlja
 * i razbija topao ton, pa se boja uzima iz teme i prati svetlu i tamnu varijantu.
 * `color` menja ton senke, npr. kartica dogadjaja baca senku u boji svoje kategorije.
 */
fun Modifier.warmShadow(elevation: Dp, shape: Shape, color: Color? = null): Modifier = composed {
    val shadowColor = color ?: MaterialTheme.orbitAccents.shadow
    shadow(elevation = elevation, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
}
