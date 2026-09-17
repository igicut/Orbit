package com.example.orbit.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta je izvedena iz logotipa (mipmap/ic_launcher_round.png). Tacne boje ikonice:
 * kopno #0DA84A, svetlo zelena #8DD565, okean #00A1CC, crveni pin #E64139,
 * narandzasti pin #F6680B, zuta zvezda #FCD92E, plavi pin #0086E0.
 * Boje ispod su prigusene verzije istih tonova, da ekran ne vice kao ikonica.
 * Odnosi kontrasta u komentarima su mereni po WCAG formuli.
 */

// ---- svetla tema ----

/** Trava iz logotipa, potamnjena da beo tekst na dugmetu ima 4.80:1 */
val GrassGreen = Color(0xFF4F7D4E)
val GrassGreenContainer = Color(0xFFDCE9D6)
val OnGrassGreenContainer = Color(0xFF1D3320)

/** Plavi pin, potamnjen na 4.77:1 sa belim tekstom */
val DustyBlue = Color(0xFF4E7796)
val DustyBlueContainer = Color(0xFFD8E4EE)
val OnDustyBlueContainer = Color(0xFF172A38)

/** Zuta zvezda, prigusena; belo na njoj pada (2.10:1), pa ide tamno mastilo 5.93:1 */
val MustardAmber = Color(0xFFE3A857)
val MustardAmberContainer = Color(0xFFF7E7C8)
val OnMustardAmberContainer = Color(0xFF402D0E)

/** Crveni pin, prigusen; belo na njemu 5.05:1 */
val Terracotta = Color(0xFFB4503F)
val TerracottaContainer = Color(0xFFF6DCD7)
val OnTerracottaContainer = Color(0xFF45160F)

val WarmCream = Color(0xFFF7F0E3)
val SoftOffWhite = Color(0xFFFFFDF8)
val WarmSand = Color(0xFFEBE3D4)
val WarmSandDim = Color(0xFFE5DDCD)
val WarmSandHigh = Color(0xFFF1E9DA)

/** Topla ugljena, ne cisto crna; 10.98:1 na kremu */
val WarmCharcoal = Color(0xFF3A332A)
val WarmStone = Color(0xFF6B6151)
val WarmOutline = Color(0xFFA79C89)
val WarmOutlineVariant = Color(0xFFD9D0BE)

// ---- tamna tema ----

val GrassGreenDark = Color(0xFF9CC49A)
val OnGrassGreenDark = Color(0xFF10290F)
val GrassGreenContainerDark = Color(0xFF35543A)
val OnGrassGreenContainerDark = Color(0xFFC9E5C4)

val DustyBlueDark = Color(0xFFA9C4DA)
val OnDustyBlueDark = Color(0xFF12293A)
val DustyBlueContainerDark = Color(0xFF2F4A5E)
val OnDustyBlueContainerDark = Color(0xFFD2E4F0)

val MustardAmberDark = Color(0xFFF0C589)
val OnMustardAmberDark = Color(0xFF3A2A0E)
val MustardAmberContainerDark = Color(0xFF5A431F)
val OnMustardAmberContainerDark = Color(0xFFF7E1C0)

val TerracottaDark = Color(0xFFE9A08F)
val OnTerracottaDark = Color(0xFF45160F)
val TerracottaContainerDark = Color(0xFF7A3428)
val OnTerracottaContainerDark = Color(0xFFF8D9D1)

val WarmBackgroundDark = Color(0xFF1B1813)
val WarmSurfaceDark = Color(0xFF221E18)
val WarmSurfaceLowestDark = Color(0xFF141109)
val WarmSurfaceLowDark = Color(0xFF1F1B15)
val WarmSurfaceContainerDark = Color(0xFF26221B)
val WarmSurfaceHighDark = Color(0xFF312C24)
val WarmSurfaceHighestDark = Color(0xFF3C362C)
val WarmSurfaceVariantDark = Color(0xFF3A332A)

/** 13.50:1 na tamnoj povrsini */
val WarmInkDark = Color(0xFFEFE7D9)
val WarmStoneDark = Color(0xFFC7BCA8)
val WarmOutlineDark = Color(0xFF8A8071)
val WarmOutlineVariantDark = Color(0xFF4A4237)

// ---- statusi prijave, citljivi kao obican tekst na pozadini ----

/** 6.32:1 na kremu; `primary` bi na tekstu bio 4.23:1 */
val RegisteredGreen = Color(0xFF37613A)

/** 7.03:1 na kremu */
val NoSpotsRed = Color(0xFF8C3428)

/*
 * Kategorije: raspored nijansi je iz orbit-design skilla (plum, plava, terakota, senf,
 * skriljac, sumska, prasnjava ruza, topli pesak). Tonovi su produbljeni u odnosu na
 * skill, jer belo mastilo na bloku kartice mora da predje 4.5:1 - skill-ove vrednosti
 * daju 2.10:1 za senf i 2.66:1 za pesak.
 */

val CategoryMusic = Color(0xFF6B4F66)
val CategorySport = Color(0xFF4E7796)
val CategoryFood = Color(0xFFB4503F)
val CategoryArt = Color(0xFF96702A)
val CategoryTech = Color(0xFF4E6B6E)
val CategoryOutdoor = Color(0xFF3F6A45)
val CategorySocial = Color(0xFF9C5A65)
val CategoryOther = Color(0xFF7E6A4F)

val CategoryMusicDark = Color(0xFFC9A9C2)
val CategorySportDark = Color(0xFFA9C4DA)
val CategoryFoodDark = Color(0xFFE9A08F)
val CategoryArtDark = Color(0xFFE6C184)
val CategoryTechDark = Color(0xFFA8C6C9)
val CategoryOutdoorDark = Color(0xFF9CC49A)
val CategorySocialDark = Color(0xFFDFA7AF)
val CategoryOtherDark = Color(0xFFD0BC9C)

// ---- markeri na mapi ----

/**
 * Terakota iz palete; nije `Terracotta` (#B4503F), jer je ona potamnjena
 * zbog belog teksta na dugmetu, a na pinu teksta nema.
 */
val PinTerracotta = Color(0xFFC1594A)

/** Izabran marker; narandzasti pin sa logotipa */
val PinOrange = Color(0xFFF6680B)

/** Tacka trenutne lokacije, namerno ostaje plava */
val PinBlue = Color(0xFF0086E0)
