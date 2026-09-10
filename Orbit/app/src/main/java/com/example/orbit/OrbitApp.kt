package com.example.orbit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.orbit.ui.navigation.OrbitBottomBar
import com.example.orbit.ui.navigation.OrbitDestinations
import com.example.orbit.ui.navigation.OrbitNavHost

/**
 * The app shell: a bottom bar that persists across the three main areas, with
 * the NavHost inside it.
 *
 * The bar is hidden on full-screen destinations (create, detail) by checking the
 * current route against OrbitDestinations.bottomBarRoutes.
 *
 * Padding from this Scaffold is passed down to the NavHost, so each screen's own
 * Scaffold sits in the space above the bar. That is what keeps a screen's
 * floating action button clear of the navigation bar instead of behind it.
 */
@Composable
fun OrbitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in OrbitDestinations.bottomBarRoutes) {
                OrbitBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            // Standard bottom-navigation behaviour:
                            //  popUpTo + saveState  - do not stack tabs on top of
                            //      each other, but remember each tab's scroll position
                            //  launchSingleTop      - tapping the current tab again
                            //      does not push a second copy
                            //  restoreState         - return to a tab as you left it
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        OrbitNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
