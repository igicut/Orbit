package com.example.orbit.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.orbit.R

/** Tri glavna taba, redom kao u donjoj navigaciji */
enum class OrbitTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SEARCH(OrbitDestinations.SEARCH, R.string.tab_events, Icons.Filled.Search),
    MAP(OrbitDestinations.MAP, R.string.tab_map, Icons.Filled.Place),
    ACCOUNT(OrbitDestinations.ACCOUNT, R.string.tab_account, Icons.Filled.Person),
}

@Composable
fun OrbitBottomBar(
    currentRoute: String?,
    onTabSelected: (OrbitTab) -> Unit,
) {
    NavigationBar {
        OrbitTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
