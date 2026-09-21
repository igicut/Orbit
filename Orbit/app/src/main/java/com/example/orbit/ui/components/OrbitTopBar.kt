package com.example.orbit.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.orbit.R

/** Niza od podrazumevanih 64 dp, jer je traka zauzimala previse prostora */
private val BAR_HEIGHT = 48.dp

/**
 * Gornja traka za ekrane na koje se ulazi iz drugog ekrana.
 * Tabovi (Explore, Map, Plans, Account) je nemaju.
 *
 * Providna je namerno: surface je svetliji od background, pa je obojena traka pravila
 * svetlu prugu na vrhu koje Explore nema. Ostaju samo strelica i naslov na istoj podlozi.
 *
 * Omotac oko Material trake, a ne sopstveni Surface, da bi razmak za statusnu traku
 * i dalje racunao Material sam.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitTopBar(
    onNavigate: () -> Unit,
    title: String = "",
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationDescription: String = stringResource(R.string.detail_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { if (title.isNotEmpty()) Text(title) },
        expandedHeight = BAR_HEIGHT,
        // Spoljni Scaffold u OrbitApp vec ostavlja mesto za statusnu traku;
        // bez ove nule razmak se racuna dva puta i vrh ekrana odskace
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = onNavigate) {
                Icon(navigationIcon, contentDescription = navigationDescription)
            }
        },
        actions = actions,
    )
}

/** Ista traka, ali sa iksom: forma se otkazuje, ne vraca korak nazad */
@Composable
fun OrbitFormTopBar(
    onCancel: () -> Unit,
    title: String,
) {
    OrbitTopBar(
        onNavigate = onCancel,
        title = title,
        navigationIcon = Icons.Filled.Close,
        navigationDescription = stringResource(R.string.common_cancel),
    )
}
