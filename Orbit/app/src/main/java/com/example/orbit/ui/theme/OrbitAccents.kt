package com.example.orbit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.orbit.domain.model.EventCategory

/**
 * Boje koje nemaju svoju ulogu u Material shemi: po jedna za svaku kategoriju
 * i dva tona za status prijave. Idu kroz CompositionLocal da bi pratile temu,
 * umesto da svaka komponenta sama proverava da li je mrak.
 */
data class OrbitAccents(
    val registered: Color,
    val noSpots: Color,
    /** Mastilo na punom bloku kategorije; boje su birane da ovo uvek prodje 4.5:1 */
    val onCategory: Color,
    private val categories: Map<EventCategory, Color>,
) {
    fun forCategory(category: EventCategory): Color = categories.getValue(category)
}

private val LightAccents = OrbitAccents(
    registered = RegisteredGreen,
    noSpots = NoSpotsRed,
    onCategory = Color.White,
    categories = mapOf(
        EventCategory.MUSIC to CategoryMusic,
        EventCategory.SPORT to CategorySport,
        EventCategory.FOOD to CategoryFood,
        EventCategory.ART to CategoryArt,
        EventCategory.TECH to CategoryTech,
        EventCategory.OUTDOOR to CategoryOutdoor,
        EventCategory.SOCIAL to CategorySocial,
        EventCategory.OTHER to CategoryOther,
    ),
)

private val DarkAccents = OrbitAccents(
    registered = GrassGreenDark,
    noSpots = TerracottaDark,
    // U mraku su boje kategorija svetle, pa mastilo ide obrnuto
    onCategory = WarmSurfaceDark,
    categories = mapOf(
        EventCategory.MUSIC to CategoryMusicDark,
        EventCategory.SPORT to CategorySportDark,
        EventCategory.FOOD to CategoryFoodDark,
        EventCategory.ART to CategoryArtDark,
        EventCategory.TECH to CategoryTechDark,
        EventCategory.OUTDOOR to CategoryOutdoorDark,
        EventCategory.SOCIAL to CategorySocialDark,
        EventCategory.OTHER to CategoryOtherDark,
    ),
)

internal fun accentsFor(darkTheme: Boolean): OrbitAccents = if (darkTheme) DarkAccents else LightAccents

internal val LocalOrbitAccents = staticCompositionLocalOf { LightAccents }

/** Kratak pristup, kao MaterialTheme.colorScheme */
val MaterialTheme.orbitAccents: OrbitAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalOrbitAccents.current
