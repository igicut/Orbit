package com.example.orbit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.ui.theme.warmShadow

/** Tri glavna taba, redom kao u donjoj navigaciji */
enum class OrbitTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SEARCH(OrbitDestinations.SEARCH, R.string.tab_explore, Icons.Filled.Search),
    MAP(OrbitDestinations.MAP, R.string.tab_map, Icons.Filled.Place),
    PLANS(OrbitDestinations.PLANS, R.string.tab_plans, Icons.Filled.DateRange),
    ACCOUNT(OrbitDestinations.ACCOUNT, R.string.tab_account, Icons.Filled.Person),
}

/** Pin stoji izmedju druge i trece stavke, pa je sredina peto mesto od pet */
private const val PIN_SLOT = 2

/** Visina same pilule; Material traka je 80 dp, ovde je dovoljno 64 */
private val BAR_HEIGHT = 64.dp

/** Odmaci pilule od ivica ekrana, da traka lebdi umesto da bude zalepljena */
private val BAR_MARGIN = 16.dp

/** Stranica kvadrata od kog nastaje pin; ujedno i meta za dodir */
private val PIN_SIZE = 44.dp

/** Koliko pin viri iznad pilule, da izgleda prikacen za nju a ne kao cetvrti tab */
private val PIN_LIFT = 10.dp

/** Prostor iznad pilule za deo pina koji viri */
private val PIN_OVERHANG = 16.dp

/**
 * Prostor koji plutajuca traka zauzima na dnu ekrana.
 * Sadrzaj ide ispod nje, pa liste i skrolovane kolone dodaju ovo na svoje dno
 * da poslednja kartica ne ostane zauvek sakrivena.
 */
val orbitBottomBarSpace: Dp
    @Composable get() = PIN_OVERHANG + BAR_HEIGHT + BAR_MARGIN +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Razmak za sistemsku navigaciju.
 * Ekrani bez plutajuce trake ga dodaju na dno sadrzaja, jer NavHost vise ne rezervise dno.
 */
val systemNavSpace: Dp
    @Composable get() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Neizabran tab je ista ugljena, samo tisa; ne druga boja */
private const val UNSELECTED_ALPHA = 0.6f

/** Pilula izabranog taba: siroka kao ikonica sa razmakom, blaga zelena */
private val INDICATOR_WIDTH = 48.dp
private val INDICATOR_HEIGHT = 28.dp
private const val INDICATOR_ALPHA = 0.15f

/**
 * Donja navigacija i pravljenje dogadjaja u jednom komadu: pilula sa tri taba
 * i pin za novi dogadjaj. Postoji samo ovde, ekrani je ne deklarisu sami.
 */
@Composable
fun OrbitBottomBar(
    currentRoute: String?,
    onTabSelected: (OrbitTab) -> Unit,
    onCreateClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Aplikacija je edge to edge; Material traka je sama sklanjala sistemsku
            // navigaciju, obicna Surface to ne radi, pa bi pilula zavrsila ispod nje
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = BAR_MARGIN, end = BAR_MARGIN, top = PIN_OVERHANG, bottom = BAR_MARGIN),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .warmShadow(elevation = 8.dp, shape = CircleShape),
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = OrbitTab.entries
                // Pet jednakih mesta: dva taba, pin, pa jos dva taba
                repeat(tabs.size + 1) { slot ->
                    if (slot == PIN_SLOT) {
                        // Prazno mesto; pin se crta van pilule, da moze da viri iznad nje
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val tab = tabs[if (slot < PIN_SLOT) slot else slot - 1]
                        TabItem(
                            tab = tab,
                            selected = currentRoute == tab.route,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        CreateEventPin(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -PIN_LIFT),
        )
    }
}

/** Ikonica i naziv jedan ispod drugog; ceo stubac je meta za dodir */
@Composable
private fun TabItem(
    tab: OrbitTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tab.labelRes)
    val color by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = UNSELECTED_ALPHA)
        },
        label = "tabColor",
    )
    // Pilula iza ikonice izabranog taba, kao u Material traci; boja sama je slab znak
    val indicator by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = INDICATOR_ALPHA)
        } else {
            Color.Transparent
        },
        label = "tabIndicator",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = INDICATOR_WIDTH, height = INDICATOR_HEIGHT)
                .background(color = indicator, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pin u obliku kapi: kvadrat sa tri zaobljena ugla, zarotiran za 45 stepeni,
 * pa ostar ugao gleda nadole. Bez rucnog racunanja putanje.
 */
@Composable
private fun CreateEventPin(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinShape = RoundedCornerShape(
        topStartPercent = 50,
        topEndPercent = 50,
        bottomEndPercent = 0,
        bottomStartPercent = 50,
    )

    Surface(
        shape = pinShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(PIN_SIZE)
            .rotate(45f)
            .warmShadow(elevation = 6.dp, shape = pinShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.account_create_event),
                tint = MaterialTheme.colorScheme.onPrimary,
                // Vraca plus u uspravan polozaj, jer je ceo pin zarotiran
                modifier = Modifier.rotate(-45f),
            )
        }
    }
}
